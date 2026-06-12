package com.healthconnect.exporter.sync

import com.healthconnect.exporter.core.AppException
import com.healthconnect.exporter.core.Logger
import com.healthconnect.exporter.core.Result
import com.healthconnect.exporter.healthconnect.HealthConnectManager
import com.healthconnect.exporter.network.BatchIngestRequest
import com.healthconnect.exporter.network.HcgatewayApi
import com.healthconnect.exporter.network.IngestRecord
import com.healthconnect.exporter.storage.AppDatabase
import com.healthconnect.exporter.storage.NormalizedHealthRecord
import com.healthconnect.exporter.storage.STATUS_ACKNOWLEDGED
import com.healthconnect.exporter.storage.STATUS_FAILED
import com.healthconnect.exporter.storage.STATUS_IN_FLIGHT
import com.healthconnect.exporter.storage.STATUS_PENDING
import com.healthconnect.exporter.storage.SyncCursor
import com.google.gson.Gson
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface SyncRepository {
    suspend fun performSync(
        enabledTypes: Set<String>,
        baseUrl: String,
        authToken: String,
        deviceId: String
    ): Result<SyncResult>
    suspend fun performRepairSync(
        daysBack: Int,
        recordType: String?,
        baseUrl: String,
        authToken: String,
        deviceId: String
    ): Result<SyncResult>
}

data class SyncResult(
    val recordsRead: Int = 0,
    val recordsPersisted: Int = 0,
    val recordsAcknowledged: Int = 0,
    val recordsRejected: Int = 0,
    val cursorsAdvanced: Int = 0,
    val errors: List<String> = emptyList()
)

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val healthConnectManager: HealthConnectManager,
    private val api: HcgatewayApi,
    private val gson: Gson,
    private val logger: Logger
) : SyncRepository {

    override suspend fun performSync(
        enabledTypes: Set<String>,
        baseUrl: String,
        authToken: String,
        deviceId: String
    ): Result<SyncResult> {
        val result = SyncResult()
        val errors = mutableListOf<String>()

        if (!healthConnectManager.hasAllPermissions(healthConnectManager.supportedPermissions)) {
            return Result.failure(AppException.PermissionDeniedException())
        }

        val cursors = database.syncCursorDao().getAll().associateBy { it.recordType }
        val lookbackMs = 24 * 60 * 60 * 1000L

        for (recordType in enabledTypes) {
            try {
                val cursor = cursors[recordType]
                val startTimeMs = if (cursor != null) {
                    maxOf(cursor.highWatermarkMs - cursor.rollingLookbackMs, 0)
                } else {
                    System.currentTimeMillis() - lookbackMs
                }
                val endTimeMs = System.currentTimeMillis()

                database.syncCursorDao().updateLastAttempt(recordType, System.currentTimeMillis())

                val (start, end) = healthConnectManager.computeLookbackRange(endTimeMs - startTimeMs)
                val readResult = healthConnectManager.readAllEnabled(start, end, setOf(recordType))

                when (readResult) {
                    is Result.Failure -> {
                        errors.add("$recordType read failed: ${readResult.error.message}")
                        logger.e(TAG, "Failed to read $recordType", readResult.error)
                        continue
                    }
                    is Result.Success -> {
                        val bundles = readResult.data[recordType] ?: emptyList()
                        val rawRecords = bundles.map { bundle ->
                            com.healthconnect.exporter.storage.RawHealthRecord(
                                recordType = bundle.recordType,
                                healthConnectId = bundle.healthConnectId,
                                rawPayload = gson.toJson(bundle.payload)
                            )
                        }
                        database.rawHealthRecordDao().insertAll(rawRecords)

                        val normalizedRecords = bundles.map { bundle ->
                            val deduplicationKey = NormalizedHealthRecord.buildDeduplicationKey(
                                bundle.recordType,
                                bundle.healthConnectId,
                                bundle.timestamp
                            )
                            NormalizedHealthRecord(
                                recordType = bundle.recordType,
                                healthConnectId = bundle.healthConnectId,
                                deduplicationKey = deduplicationKey,
                                payload = gson.toJson(bundle.payload),
                                capturedAt = bundle.timestamp
                            )
                        }
                        database.normalizedHealthRecordDao().insertAll(normalizedRecords)

                        val pending = database.normalizedHealthRecordDao()
                            .getByStatuses(listOf(STATUS_PENDING), limit = BATCH_SIZE)

                        if (pending.isNotEmpty()) {
                            val batchId = UUID.randomUUID().toString()
                            val request = BatchIngestRequest(
                                records = pending.map { record ->
                                    IngestRecord(
                                        id = record.healthConnectId,
                                        type = record.recordType,
                                        payload = gson.fromJson(record.payload, Map::class.java)
                                            .mapValues { it.value ?: "" },
                                        capturedAt = record.capturedAt,
                                        deduplicationKey = record.deduplicationKey
                                    )
                                },
                                deviceId = deviceId,
                                batchId = batchId
                            )

                            try {
                                database.normalizedHealthRecordDao().updateStatusBatch(
                                    pending.map { it.id },
                                    STATUS_IN_FLIGHT
                                )

                                val response = api.ingestBatch(
                                    authorization = "Bearer $authToken",
                                    deviceId = deviceId,
                                    request = request
                                )

                                if (response.isSuccessful) {
                                    val body = response.body()!!
                                    val ackKeys = body.acknowledged.map { it.deduplicationKey }.toSet()
                                    val rejKeys = body.rejected.map { it.deduplicationKey }.toSet()

                                    for (record in pending) {
                                        when {
                                            record.deduplicationKey in ackKeys -> {
                                                database.normalizedHealthRecordDao().updateStatus(
                                                    record.id,
                                                    STATUS_ACKNOWLEDGED
                                                )
                                            }
                                            record.deduplicationKey in rejKeys -> {
                                                database.normalizedHealthRecordDao().recordAttempt(
                                                    record.id,
                                                    STATUS_FAILED,
                                                    System.currentTimeMillis(),
                                                    "Rejected by server"
                                                )
                                            }
                                            else -> {
                                                database.normalizedHealthRecordDao().recordAttempt(
                                                    record.id,
                                                    STATUS_PENDING,
                                                    System.currentTimeMillis(),
                                                    "Not in response"
                                                )
                                            }
                                        }
                                    }

                                    if (body.success) {
                                        val maxTimestamp = pending.maxOfOrNull { it.capturedAt } ?: endTimeMs
                                        database.syncCursorDao().advanceCursor(
                                            recordType = recordType,
                                            watermark = maxTimestamp,
                                            syncAt = System.currentTimeMillis()
                                        )
                                    }

                                    logger.i(TAG, "Batch $batchId: ${body.acknowledged.size} ack, ${body.rejected.size} rej")
                                } else {
                                    database.normalizedHealthRecordDao().updateStatusBatch(
                                        pending.map { it.id },
                                        STATUS_PENDING,
                                        "HTTP ${response.code()}"
                                    )
                                    errors.add("$recordType batch failed: HTTP ${response.code()}")
                                }
                            } catch (e: Exception) {
                                database.normalizedHealthRecordDao().updateStatusBatch(
                                    pending.map { it.id },
                                    STATUS_PENDING,
                                    e.message
                                )
                                errors.add("$recordType batch error: ${e.message}")
                                logger.e(TAG, "Batch ingest failed", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                errors.add("$recordType sync error: ${e.message}")
                logger.e(TAG, "Sync failed for $recordType", e)
            }
        }

        val totalPending = database.normalizedHealthRecordDao().countByStatus(STATUS_PENDING)
        val totalInFlight = database.normalizedHealthRecordDao().countByStatus(STATUS_IN_FLIGHT)
        val totalAck = database.normalizedHealthRecordDao().countByStatus(STATUS_ACKNOWLEDGED)

        return Result.success(
            SyncResult(
                recordsRead = result.recordsRead,
                recordsPersisted = result.recordsPersisted,
                recordsAcknowledged = totalAck,
                recordsRejected = totalInFlight,
                cursorsAdvanced = cursors.size,
                errors = errors
            )
        )
    }

    override suspend fun performRepairSync(
        daysBack: Int,
        recordType: String?,
        baseUrl: String,
        authToken: String,
        deviceId: String
    ): Result<SyncResult> {
        val errors = mutableListOf<String>()
        val sinceMs = System.currentTimeMillis() - (daysBack * 24L * 60 * 60 * 1000)
        val types = recordType?.let { setOf(it) } ?: setOf(
            "StepsRecord", "HeartRateRecord", "SleepSessionRecord",
            "DistanceRecord", "ActiveCaloriesBurnedRecord",
            "WeightRecord", "BloodPressureRecord", "WorkoutRecord"
        )

        for (type in types) {
            try {
                val records = database.normalizedHealthRecordDao().getInRange(type, sinceMs, System.currentTimeMillis())
                val pendingRecords = records.filter { it.status != STATUS_ACKNOWLEDGED }

                if (pendingRecords.isEmpty()) continue

                val batchId = UUID.randomUUID().toString()
                val request = BatchIngestRequest(
                    records = pendingRecords.map { record ->
                        IngestRecord(
                            id = record.healthConnectId,
                            type = record.recordType,
                            payload = gson.fromJson(record.payload, Map::class.java)
                                .mapValues { it.value ?: "" },
                            capturedAt = record.capturedAt,
                            deduplicationKey = record.deduplicationKey
                        )
                    },
                    deviceId = deviceId,
                    batchId = batchId
                )

                database.normalizedHealthRecordDao().updateStatusBatch(
                    pendingRecords.map { it.id },
                    STATUS_IN_FLIGHT
                )

                val response = api.ingestBatch(
                    authorization = "Bearer $authToken",
                    deviceId = deviceId,
                    request = request
                )

                if (response.isSuccessful) {
                    val body = response.body()!!
                    val ackKeys = body.acknowledged.map { it.deduplicationKey }.toSet()
                    val rejKeys = body.rejected.map { it.deduplicationKey }.toSet()

                    for (record in pendingRecords) {
                        when {
                            record.deduplicationKey in ackKeys -> {
                                database.normalizedHealthRecordDao().updateStatus(record.id, STATUS_ACKNOWLEDGED)
                            }
                            record.deduplicationKey in rejKeys -> {
                                database.normalizedHealthRecordDao().recordAttempt(
                                    record.id, STATUS_FAILED, System.currentTimeMillis(), "Rejected"
                                )
                            }
                            else -> {
                                database.normalizedHealthRecordDao().recordAttempt(
                                    record.id, STATUS_PENDING, System.currentTimeMillis(), "Not in response"
                                )
                            }
                        }
                    }

                    val maxTs = pendingRecords.maxOfOrNull { it.capturedAt } ?: System.currentTimeMillis()
                    database.syncCursorDao().advanceCursor(type, maxTs, System.currentTimeMillis())
                } else {
                    database.normalizedHealthRecordDao().updateStatusBatch(
                        pendingRecords.map { it.id },
                        STATUS_PENDING,
                        "HTTP ${response.code()}"
                    )
                    errors.add("$type repair failed: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                errors.add("$type repair error: ${e.message}")
                logger.e(TAG, "Repair sync failed for $type", e)
            }
        }

        return Result.success(SyncResult(errors = errors))
    }

    companion object {
        private const val TAG = "SyncRepository"
        private const val BATCH_SIZE = 100
    }
}
