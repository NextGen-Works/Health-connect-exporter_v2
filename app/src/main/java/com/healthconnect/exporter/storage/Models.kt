package com.healthconnect.exporter.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val STATUS_PENDING = "PENDING"
const val STATUS_IN_FLIGHT = "IN_FLIGHT"
const val STATUS_ACKNOWLEDGED = "ACKNOWLEDGED"
const val STATUS_FAILED = "FAILED"

@Entity(tableName = "raw_health_records")
data class RawHealthRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordType: String,
    val healthConnectId: String,
    val rawPayload: String,
    val capturedAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = 1
)

@Entity(
    tableName = "normalized_health_records",
    indices = [
        Index(value = ["deduplicationKey"], unique = true),
        Index(value = ["status"]),
        Index(value = ["recordType"]),
        Index(value = ["capturedAt"])
    ]
)
data class NormalizedHealthRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordType: String,
    val healthConnectId: String,
    val deduplicationKey: String,
    val payload: String,
    val capturedAt: Long,
    val ingestedAt: Long = System.currentTimeMillis(),
    val status: String = STATUS_PENDING,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null,
    val serverResponseId: String? = null
) {
    companion object {
        fun buildDeduplicationKey(type: String, healthConnectId: String, timestamp: Long): String {
            return "${type}_${healthConnectId}_$timestamp"
        }
    }
}

@Entity(
    tableName = "sync_cursors",
    indices = [
        Index(value = ["recordType"], unique = true)
    ]
)
data class SyncCursor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordType: String,
    val highWatermarkMs: Long,
    val rollingLookbackMs: Long = 24 * 60 * 60 * 1000L,
    val lastSyncAttemptAt: Long? = null,
    val lastSuccessfulSyncAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "app_event_log",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["level"])
    ]
)
data class AppEventLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

@Entity(tableName = "permission_state")
data class PermissionState(
    @PrimaryKey
    val permission: String,
    val granted: Boolean,
    val lastCheckedAt: Long = System.currentTimeMillis()
)
