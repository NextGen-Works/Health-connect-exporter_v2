package com.healthconnect.exporter.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RawHealthRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RawHealthRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<RawHealthRecord>): List<Long>

    @Query("SELECT * FROM raw_health_records WHERE recordType = :type AND healthConnectId = :hcId LIMIT 1")
    suspend fun findByTypeAndId(type: String, hcId: String): RawHealthRecord?

    @Query("SELECT COUNT(*) FROM raw_health_records")
    suspend fun count(): Int
}

@Dao
interface NormalizedHealthRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: NormalizedHealthRecord): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<NormalizedHealthRecord>): List<Long>

    @Update
    suspend fun update(record: NormalizedHealthRecord)

    @Update
    suspend fun updateAll(records: List<NormalizedHealthRecord>)

    @Query("SELECT * FROM normalized_health_records WHERE status = :status LIMIT :limit")
    suspend fun getByStatus(status: String, limit: Int = 100): List<NormalizedHealthRecord>

    @Query("SELECT * FROM normalized_health_records WHERE status IN (:statuses) LIMIT :limit")
    suspend fun getByStatuses(statuses: List<String>, limit: Int = 100): List<NormalizedHealthRecord>

    @Query("SELECT COUNT(*) FROM normalized_health_records WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM normalized_health_records WHERE status = :status AND recordType = :type")
    suspend fun countByStatusAndType(status: String, type: String): Int

    @Query("SELECT * FROM normalized_health_records WHERE deduplicationKey = :key LIMIT 1")
    suspend fun findByDeduplicationKey(key: String): NormalizedHealthRecord?

    @Query("SELECT * FROM normalized_health_records WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<NormalizedHealthRecord>

    @Query("UPDATE normalized_health_records SET status = :newStatus, lastAttemptAt = :attemptAt WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE normalized_health_records SET status = :newStatus, errorMessage = :error WHERE id IN (:ids)")
    suspend fun updateStatusBatch(ids: List<Long>, newStatus: String, error: String? = null)

    @Query("UPDATE normalized_health_records SET status = :status, retryCount = retryCount + 1, lastAttemptAt = :attemptAt, errorMessage = :error WHERE id = :id")
    suspend fun recordAttempt(id: Long, status: String, attemptAt: Long, error: String? = null)

    @Query("""
        SELECT * FROM normalized_health_records
        WHERE capturedAt >= :sinceMs AND capturedAt <= :untilMs
        AND recordType = :recordType
        ORDER BY capturedAt ASC
    """)
    suspend fun getInRange(recordType: String, sinceMs: Long, untilMs: Long): List<NormalizedHealthRecord>

    @Query("DELETE FROM normalized_health_records WHERE status = :status")
    suspend fun deleteByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM normalized_health_records")
    suspend fun totalCount(): Int
}

@Dao
interface SyncCursorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursor)

    @Query("SELECT * FROM sync_cursors WHERE recordType = :recordType LIMIT 1")
    suspend fun getByType(recordType: String): SyncCursor?

    @Query("SELECT * FROM sync_cursors")
    suspend fun getAll(): List<SyncCursor>

    @Query("SELECT * FROM sync_cursors")
    fun observeAll(): Flow<List<SyncCursor>>

    @Query("UPDATE sync_cursors SET highWatermarkMs = :watermark, lastSuccessfulSyncAt = :syncAt, updatedAt = :updatedAt WHERE recordType = :recordType")
    suspend fun advanceCursor(recordType: String, watermark: Long, syncAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_cursors SET lastSyncAttemptAt = :attemptAt WHERE recordType = :recordType")
    suspend fun updateLastAttempt(recordType: String, attemptAt: Long)
}

@Dao
interface AppEventLogDao {
    @Insert
    suspend fun insert(entry: AppEventLogEntity): Long

    @Query("SELECT * FROM app_event_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AppEventLogEntity>

    @Query("SELECT * FROM app_event_log WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getSince(sinceTimestamp: Long): List<AppEventLogEntity>

    @Query("SELECT * FROM app_event_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AppEventLogEntity>>

    @Query("DELETE FROM app_event_log")
    suspend fun deleteAll()

    @Query("DELETE FROM app_event_log WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM app_event_log")
    suspend fun count(): Int
}

@Dao
interface PermissionStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PermissionState)

    @Query("SELECT * FROM permission_state")
    suspend fun getAll(): List<PermissionState>

    @Query("SELECT granted FROM permission_state WHERE permission = :permission")
    suspend fun isGranted(permission: String): Boolean?

    @Query("SELECT * FROM permission_state WHERE permission = :permission LIMIT 1")
    suspend fun getByPermission(permission: String): PermissionState?
}
