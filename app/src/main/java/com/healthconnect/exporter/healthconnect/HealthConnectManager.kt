package com.healthconnect.exporter.healthconnect

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WorkoutRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthconnect.exporter.core.AppException
import com.healthconnect.exporter.core.Logger
import com.healthconnect.exporter.core.Result
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class HealthDataBundle(
    val recordType: String,
    val healthConnectId: String,
    val timestamp: Long,
    val payload: Map<String, Any>
)

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    private val gson = Gson()
    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    val supportedPermissions: Set<HealthPermission> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(WorkoutRecord::class)
    )

    fun isAvailable(): HealthConnectClient.Availability {
        return HealthConnectClient.getSdkStatus(context)
    }

    fun buildPermissionIntent(permissions: Set<HealthPermission>): Intent {
        return HealthConnectClient.createPermissionControllerIntent(context)
    }

    fun buildPermissionsRationaleIntent(): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("package:${context.packageName}")
        return intent
    }

    suspend fun hasAllPermissions(permissions: Set<HealthPermission>): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            permissions.all { it in granted }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to check permissions", e)
            false
        }
    }

    suspend fun getGrantedPermissions(): Set<HealthPermission> {
        return try {
            healthConnectClient.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            logger.e(TAG, "Failed to get granted permissions", e)
            emptySet()
        }
    }

    suspend fun readSteps(startTime: Instant, endTime: Instant): Result<List<HealthDataBundle>> {
        return readRecords<StepsRecord>(startTime, endTime) { record ->
            mapOf(
                "count" to record.count,
                "sourceAppInfo" to (record.metadata.dataOrigin.packageName)
            )
        }
    }

    suspend fun readHeartRate(startTime: Instant, endTime: Instant): Result<List<HealthDataBundle>> {
        return readRecords<HeartRateRecord>(startTime, endTime) { record ->
            mapOf(
                "samples" to record.samples.map { sample ->
                    mapOf(
                        "beatsPerMinute" to sample.beatsPerMinute,
                        "time" to sample.time.toEpochMilli()
                    )
                },
                "startZoneOffset" to (record.startZoneOffset?.id ?: "UTC"),
                "endZoneOffset" to (record.endZoneOffset?.id ?: "UTC")
            )
        }
    }

    suspend fun readSleepSessions(startTime: Instant, endTime: Instant): Result<List<HealthDataBundle>> {
        return readRecords<SleepSessionRecord>(startTime, endTime) { record ->
            mapOf(
                "title" to record.title,
                "notes" to record.notes,
                "stages" to record.stages.map { stage ->
                    mapOf(
                        "stage" to stage.stage,
                        "startZoneOffset" to (stage.startZoneOffset?.id ?: "UTC"),
                        "endZoneOffset" to (stage.endZoneOffset?.id ?: "UTC")
                    )
                }
            )
        }
    }

    suspend fun readDistance(startTime: Instant, endTime: Instant): Result<List<HealthDataBundle>> {
        return readRecords<DistanceRecord>(startTime, endTime) { record ->
            mapOf(
                "distance" to mapOf(
                    "inMeters" to record.distance.inMeters
                )
            )
        }
    }

    suspend fun readActiveCalories(startTime: Instant, endTime: Instant): Result<List<HealthDataBundle>> {
        return readRecords<ActiveCaloriesBurnedRecord>(startTime, endTime) { record ->
            mapOf(
                "energy" to mapOf(
                    "inKilocalories" to record.energy.inKilocalories
                )
            )
        }
    }

    suspend fun readWeight(startTime: Instant, endTime: Instant): Result<List<HealthDataBundle>> {
        return readRecords<WeightRecord>(startTime, endTime) { record ->
            mapOf(
                "weight" to mapOf(
                    "inKilograms" to record.weight.inKilograms
                )
            )
        }
    }

    suspend fun readBloodPressure(startTime: Instant, endTime: Instant): Result<List<HealthDataBundle>> {
        return readRecords<BloodPressureRecord>(startTime, endTime) { record ->
            mapOf(
                "systolic" to mapOf("inMmHg" to record.systolic.inMmHg),
                "diastolic" to mapOf("inMmHg" to record.diastolic.inMmHg),
                "bodyPosition" to record.bodyPosition,
                "measurementLocation" to record.measurementLocation
            )
        }
    }

    suspend fun readWorkouts(startTime: Instant, endTime: Instant): Result<List<HealthDataBundle>> {
        return readRecords<WorkoutRecord>(startTime, endTime) { record ->
            mapOf(
                "title" to record.title,
                "notes" to record.notes,
                "exerciseType" to record.exerciseType,
                "duration" to record.duration.inSeconds,
                "totalDistance" to record.totalDistance?.inMeters,
                "totalCaloriesBurned" to record.totalCaloriesBurned?.inKilocalories,
                "segments" to record.segments.size
            )
        }
    }

    private inline fun <reified T> readRecords(
        startTime: Instant,
        endTime: Instant,
        mapper: (T) -> Map<String, Any>
    ): Result<List<HealthDataBundle>> {
        return try {
            val timeRange = TimeRangeFilter.between(startTime, endTime)
            val request = ReadRecordsRequest(recordType = T::class, timeRangeFilter = timeRange)
            val response = healthConnectClient.readRecords(request)
            val typeAlias = T::class.simpleName ?: "Unknown"
            val bundles = response.records.map { record ->
                HealthDataBundle(
                    recordType = typeAlias,
                    healthConnectId = record.metadata.id,
                    timestamp = extractTimestamp(record),
                    payload = mapper(record)
                )
            }
            logger.i(TAG, "Read ${bundles.size} $typeAlias records")
            Result.success(bundles)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to read ${T::class.simpleName} records", e)
            Result.failure(AppException.HealthConnectException("Failed to read ${T::class.simpleName}", e))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractTimestamp(record: Any): Long {
        return try {
            when (record) {
                is StepsRecord -> record.startTime.toEpochMilli()
                is HeartRateRecord -> record.startTime.toEpochMilli()
                is SleepSessionRecord -> record.startTime.toEpochMilli()
                is DistanceRecord -> record.startTime.toEpochMilli()
                is ActiveCaloriesBurnedRecord -> record.startTime.toEpochMilli()
                is WeightRecord -> record.time.toEpochMilli()
                is BloodPressureRecord -> record.time.toEpochMilli()
                is WorkoutRecord -> record.startTime.toEpochMilli()
                else -> System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    suspend fun readAllEnabled(
        startTime: Instant,
        endTime: Instant,
        enabledTypes: Set<String>
    ): Result<Map<String, List<HealthDataBundle>>> {
        val results = mutableMapOf<String, List<HealthDataBundle>>()
        val typeReaders: Map<String, suspend (Instant, Instant) -> Result<List<HealthDataBundle>>> = mapOf(
            "StepsRecord" to ::readSteps,
            "HeartRateRecord" to ::readHeartRate,
            "SleepSessionRecord" to ::readSleepSessions,
            "DistanceRecord" to ::readDistance,
            "ActiveCaloriesBurnedRecord" to ::readActiveCalories,
            "WeightRecord" to ::readWeight,
            "BloodPressureRecord" to ::readBloodPressure,
            "WorkoutRecord" to ::readWorkouts
        )

        for (typeName in enabledTypes) {
            val reader = typeReaders[typeName] ?: continue
            when (val result = reader(startTime, endTime)) {
                is Result.Success -> results[typeName] = result.data
                is Result.Failure -> {
                    logger.w(TAG, "Skipping $typeName due to error: ${result.error.message}")
                }
            }
        }

        return Result.success(results)
    }

    fun computeLookbackRange(lookbackMs: Long): Pair<Instant, Instant> {
        val endTime = Instant.now()
        val startTime = endTime.minus(lookbackMs, ChronoUnit.MILLIS)
        return Pair(startTime, endTime)
    }

    companion object {
        private const val TAG = "HealthConnectManager"
    }
}
