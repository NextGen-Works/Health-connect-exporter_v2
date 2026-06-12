package com.healthconnect.exporter.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.healthconnect.exporter.core.Logger
import com.healthconnect.exporter.storage.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val database: AppDatabase,
    private val logger: Logger
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val enabledTypes = inputData.getStringArray(KEY_ENABLED_TYPES)?.toSet() ?: defaultTypes
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: return androidx.work.ListenableWorker.Result.failure()
        val authToken = inputData.getString(KEY_AUTH_TOKEN) ?: return androidx.work.ListenableWorker.Result.failure()
        val deviceId = inputData.getString(KEY_DEVICE_ID) ?: return androidx.work.ListenableWorker.Result.failure()

        logger.i(TAG, "Starting sync worker run")

        return try {
            val hasPermissions = database.permissionStateDao().getAll()
                .filter { it.granted }
                .isNotEmpty()

            if (!hasPermissions) {
                logger.w(TAG, "No permissions granted, aborting sync")
                return androidx.work.ListenableWorker.Result.failure(
                    buildDataOf(ERROR_KEY to "Permissions not granted")
                )
            }

            when (val syncResult = syncRepository.performSync(enabledTypes, baseUrl, authToken, deviceId)) {
                is com.healthconnect.exporter.core.Result.Success -> {
                    val data = syncResult.data
                    logger.i(TAG, "Sync complete: ${data.recordsRead} read, ${data.recordsAcknowledged} ack, ${data.errors.size} errors")

                    if (data.errors.isNotEmpty()) {
                        logger.w(TAG, "Sync completed with errors: ${data.errors.joinToString()}")
                    }

                    androidx.work.ListenableWorker.Result.success(
                        buildDataOf(
                            RECORDS_READ to data.recordsRead,
                            RECORDS_ACKNOWLEDGED to data.recordsAcknowledged,
                            RECORDS_REJECTED to data.recordsRejected,
                            ERRORS_COUNT to data.errors.size
                        )
                    )
                }
                is com.healthconnect.exporter.core.Result.Failure -> {
                    logger.e(TAG, "Sync failed: ${syncResult.error.message}")
                    androidx.work.ListenableWorker.Result.retry()
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Sync worker exception", e)
            androidx.work.ListenableWorker.Result.retry()
        }
    }

    private fun buildDataOf(vararg pairs: Pair<String, Any>): Data {
        val builder = Data.Builder()
        for ((key, value) in pairs) {
            when (value) {
                is Int -> builder.putInt(key, value)
                is Long -> builder.putLong(key, value)
                is String -> builder.putString(key, value)
                is Boolean -> builder.putBoolean(key, value)
            }
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME_PERIODIC = "health_connect_sync_periodic"
        private const val WORK_NAME_ONE_TIME = "health_connect_sync_one_time"
        private const val SYNC_INTERVAL_MINUTES = 30L
        const val KEY_ENABLED_TYPES = "enabled_types"
        const val KEY_BASE_URL = "base_url"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_DEVICE_ID = "device_id"
        const val RECORDS_READ = "records_read"
        const val RECORDS_ACKNOWLEDGED = "records_acknowledged"
        const val RECORDS_REJECTED = "records_rejected"
        const val ERRORS_COUNT = "errors_count"
        const val ERROR_KEY = "error"

        private val defaultTypes = setOf(
            "StepsRecord", "HeartRateRecord", "SleepSessionRecord",
            "DistanceRecord", "ActiveCaloriesBurnedRecord",
            "WeightRecord", "BloodPressureRecord", "WorkoutRecord"
        )

        fun enqueuePeriodicSync(context: Context, config: SyncConfig) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = androidx.work.Data.Builder()
                .putStringArray(KEY_ENABLED_TYPES, config.enabledTypes.toTypedArray())
                .putString(KEY_BASE_URL, config.baseUrl)
                .putString(KEY_AUTH_TOKEN, config.authToken)
                .putString(KEY_DEVICE_ID, config.deviceId)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun enqueueOneTimeSync(context: Context, config: SyncConfig) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = androidx.work.Data.Builder()
                .putStringArray(KEY_ENABLED_TYPES, config.enabledTypes.toTypedArray())
                .putString(KEY_BASE_URL, config.baseUrl)
                .putString(KEY_AUTH_TOKEN, config.authToken)
                .putString(KEY_DEVICE_ID, config.deviceId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ONE_TIME)
        }
    }
}

data class SyncConfig(
    val enabledTypes: Set<String>,
    val baseUrl: String,
    val authToken: String,
    val deviceId: String
)
