package com.healthconnect.exporter.core

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val error: AppException) : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun exceptionOrNull(): AppException? = when (this) {
        is Success -> null
        is Failure -> error
    }

    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun failure(error: AppException): Result<Nothing> = Failure(error)
    }
}

sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkException(
        message: String,
        cause: Throwable? = null,
        val code: Int? = null
    ) : AppException(message, cause)

    class DatabaseException(message: String, cause: Throwable? = null) : AppException(message, cause)
    class HealthConnectException(message: String, cause: Throwable? = null) : AppException(message, cause)
    class PermissionDeniedException(message: String = "Health Connect permissions denied") : AppException(message)
    class ConfigurationException(message: String) : AppException(message)
    class DeduplicationException(message: String) : AppException(message)
    class SyncAbortedException(message: String = "Sync aborted due to unrecoverable error") : AppException(message)
    class ServerRejectedException(
        message: String,
        val failedRecordIds: List<String> = emptyList()
    ) : AppException(message)
    class PartialAcknowledgementException(
        message: String,
        val acknowledgedIds: List<String>,
        val failedIds: List<String>
    ) : AppException(message)
}
