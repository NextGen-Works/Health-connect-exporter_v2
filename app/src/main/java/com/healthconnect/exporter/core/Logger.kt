package com.healthconnect.exporter.core

import android.content.Context
import android.util.Log
import com.healthconnect.exporter.storage.AppDatabase
import com.healthconnect.exporter.storage.AppEventLogEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

@Singleton
class Logger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _logFlow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 100)
    val logFlow: SharedFlow<LogEntry> = _logFlow.asSharedFlow()

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, t: Throwable? = null) = log(LogLevel.WARNING, tag, message, t)
    fun e(tag: String, message: String, t: Throwable? = null) = log(LogLevel.ERROR, tag, message, t)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(level = level, tag = tag, message = message, throwable = throwable)
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARNING -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
        _logFlow.tryEmit(entry)
        persistEntry(entry)
    }

    private fun persistEntry(entry: LogEntry) {
        scope.launch {
            try {
                database.eventLogDao().insert(
                    AppEventLogEntity(
                        timestamp = entry.timestamp,
                        level = entry.level.name,
                        tag = entry.tag,
                        message = entry.message,
                        stackTrace = entry.throwable?.stackTraceToString()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist log entry", e)
            }
        }
    }

    suspend fun getRecentLogs(limit: Int = 200): List<AppEventLogEntity> {
        return database.eventLogDao().getRecent(limit)
    }

    suspend fun getLogsSince(sinceTimestamp: Long): List<AppEventLogEntity> {
        return database.eventLogDao().getSince(sinceTimestamp)
    }

    suspend fun clearAllLogs() {
        database.eventLogDao().deleteAll()
    }

    companion object {
        private const val TAG = "Logger"
    }
}
