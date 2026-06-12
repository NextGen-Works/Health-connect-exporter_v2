package com.healthconnect.exporter.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RawHealthRecord::class,
        NormalizedHealthRecord::class,
        SyncCursor::class,
        AppEventLogEntity::class,
        PermissionState::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawHealthRecordDao(): RawHealthRecordDao
    abstract fun normalizedHealthRecordDao(): NormalizedHealthRecordDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun eventLogDao(): AppEventLogDao
    abstract fun permissionStateDao(): PermissionStateDao
}
