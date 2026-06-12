package com.healthconnect.exporter.ui

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthconnect.exporter.core.AppException
import com.healthconnect.exporter.core.Logger
import com.healthconnect.exporter.core.Result
import com.healthconnect.exporter.healthconnect.HealthConnectManager
import com.healthconnect.exporter.storage.AppDatabase
import com.healthconnect.exporter.storage.AppEventLogEntity
import com.healthconnect.exporter.storage.NormalizedHealthRecord
import com.healthconnect.exporter.storage.STATUS_ACKNOWLEDGED
import com.healthconnect.exporter.storage.STATUS_FAILED
import com.healthconnect.exporter.storage.STATUS_IN_FLIGHT
import com.healthconnect.exporter.storage.STATUS_PENDING
import com.healthconnect.exporter.storage.SyncCursor
import com.healthconnect.exporter.sync.SyncConfig
import com.healthconnect.exporter.sync.SyncRepository
import com.healthconnect.exporter.sync.SyncResult
import com.healthconnect.exporter.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

val Application.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object Prefs {
    val BASE_URL = stringPreferencesKey("base_url")
    val AUTH_TOKEN = stringPreferencesKey("auth_token")
    val DEVICE_ID = stringPreferencesKey("device_id")
    val ENABLED_TYPES = stringSetPreferencesKey("enabled_types")
}

data class OnboardingState(
    val isHealthConnectAvailable: Boolean = false,
    val hasPermissions: Boolean = false,
    val selectedTypes: Set<String> = emptySet(),
    val isComplete: Boolean = false
)

data class DashboardState(
    val pendingCount: Int = 0,
    val inFlightCount: Int = 0,
    val acknowledgedCount: Int = 0,
    val failedCount: Int = 0,
    val cursors: List<SyncCursor> = emptyList(),
    val lastSyncSuccess: Long? = null,
    val isSyncing: Boolean = false,
    val lastSyncResult: SyncResult? = null,
    val syncWarning: String? = null
)

data class SettingsState(
    val baseUrl: String = "",
    val authToken: String = "",
    val deviceId: String = "",
    val enabledTypes: Set<String> = emptySet()
)

data class DiagnosticsState(
    val logs: List<AppEventLogEntity> = emptyList(),
    val isRefreshing: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val healthConnectManager: HealthConnectManager,
    private val database: AppDatabase,
    private val logger: Logger
) : AndroidViewModel(application) {

    private val dataStore = application.dataStore
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        checkHealthConnect()
    }

    private fun checkHealthConnect() {
        viewModelScope.launch {
            val availability = healthConnectManager.isAvailable()
            _state.value = _state.value.copy(
                isHealthConnectAvailable = availability == androidx.health.connect.client.HealthConnectClient.Availability.SDK_AVAILABLE
            )
            if (_state.value.isHealthConnectAvailable) {
                checkPermissions()
            }
        }
    }

    fun checkPermissions() {
        viewModelScope.launch {
            val hasPermissions = healthConnectManager.hasAllPermissions(healthConnectManager.supportedPermissions)
            _state.value = _state.value.copy(hasPermissions = hasPermissions)
        }
    }

    fun toggleType(type: String) {
        val current = _state.value.selectedTypes.toMutableSet()
        if (current.contains(type)) current.remove(type) else current.add(type)
        _state.value = _state.value.copy(selectedTypes = current)
    }

    fun saveOnboarding() {
        viewModelScope.launch {
            val prefs = _state.value.selectedTypes.ifEmpty {
                setOf(
                    "StepsRecord", "HeartRateRecord", "SleepSessionRecord",
                    "DistanceRecord", "ActiveCaloriesBurnedRecord",
                    "WeightRecord", "BloodPressureRecord", "WorkoutRecord"
                )
            }
            dataStore.edit { it[Prefs.ENABLED_TYPES] = prefs }
            logger.i("Onboarding", "Completed with types: $prefs")
            _state.value = _state.value.copy(isComplete = true)
        }
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    application: Application,
    private val database: AppDatabase,
    private val syncRepository: SyncRepository,
    private val healthConnectManager: HealthConnectManager,
    private val logger: Logger
) : AndroidViewModel(application) {

    private val dataStore = application.dataStore
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        loadState()
    }

    private fun loadState() {
        viewModelScope.launch {
            val pending = database.normalizedHealthRecordDao().countByStatus(STATUS_PENDING)
            val inFlight = database.normalizedHealthRecordDao().countByStatus(STATUS_IN_FLIGHT)
            val ack = database.normalizedHealthRecordDao().countByStatus(STATUS_ACKNOWLEDGED)
            val failed = database.normalizedHealthRecordDao().countByStatus(STATUS_FAILED)
            val cursors = database.syncCursorDao().getAll()

            val lastSync = cursors.maxByOrNull { it.lastSuccessfulSyncAt ?: 0 }?.lastSuccessfulSyncAt
            val hoursSinceSync = if (lastSync != null) {
                ChronoUnit.HOURS.between(Instant.ofEpochMilli(lastSync), Instant.now())
            } else {
                Long.MAX_VALUE
            }

            val warning = when {
                hoursSinceSync > 24 -> "No successful sync in the last 24 hours"
                failed > 10 -> "$failed records failed to sync"
                else -> null
            }

            _state.value = DashboardState(
                pendingCount = pending,
                inFlightCount = inFlight,
                acknowledgedCount = ack,
                failedCount = failed,
                cursors = cursors,
                lastSyncSuccess = lastSync,
                syncWarning = warning
            )
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true)
            try {
                val prefs = dataStore.data.first()
                val baseUrl = prefs[Prefs.BASE_URL] ?: ""
                val authToken = prefs[Prefs.AUTH_TOKEN] ?: ""
                val deviceId = prefs[Prefs.DEVICE_ID] ?: ""
                val enabledTypes = prefs[Prefs.ENABLED_TYPES] ?: emptySet()

                if (baseUrl.isBlank() || authToken.isBlank()) {
                    logger.w("Dashboard", "Missing configuration")
                    return@launch
                }

                val config = SyncConfig(
                    enabledTypes = enabledTypes,
                    baseUrl = baseUrl,
                    authToken = authToken,
                    deviceId = deviceId
                )

                when (val result = syncRepository.performSync(
                    enabledTypes, baseUrl, authToken, deviceId
                )) {
                    is Result.Success -> {
                        _state.value = _state.value.copy(
                            lastSyncResult = result.data,
                            isSyncing = false
                        )
                        logger.i("Dashboard", "Sync completed: ${result.data}")
                    }
                    is Result.Failure -> {
                        _state.value = _state.value.copy(isSyncing = false)
                        logger.e("Dashboard", "Sync failed: ${result.error.message}")
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSyncing = false)
                logger.e("Dashboard", "Sync error", e)
            } finally {
                loadState()
            }
        }
    }

    fun refresh() {
        loadState()
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val logger: Logger
) : AndroidViewModel(application) {

    private val dataStore = application.dataStore
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            dataStore.data.first().let { prefs ->
                _state.value = SettingsState(
                    baseUrl = prefs[Prefs.BASE_URL] ?: "",
                    authToken = prefs[Prefs.AUTH_TOKEN] ?: "",
                    deviceId = prefs[Prefs.DEVICE_ID] ?: "",
                    enabledTypes = prefs[Prefs.ENABLED_TYPES] ?: emptySet()
                )
            }
        }
    }

    fun updateBaseUrl(url: String) {
        _state.value = _state.value.copy(baseUrl = url)
    }

    fun updateAuthToken(token: String) {
        _state.value = _state.value.copy(authToken = token)
    }

    fun updateDeviceId(id: String) {
        _state.value = _state.value.copy(deviceId = id)
    }

    fun toggleType(type: String) {
        val current = _state.value.enabledTypes.toMutableSet()
        if (current.contains(type)) current.remove(type) else current.add(type)
        _state.value = _state.value.copy(enabledTypes = current)
    }

    fun saveSettings() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[Prefs.BASE_URL] = _state.value.baseUrl
                prefs[Prefs.AUTH_TOKEN] = _state.value.authToken
                prefs[Prefs.DEVICE_ID] = _state.value.deviceId
                prefs[Prefs.ENABLED_TYPES] = _state.value.enabledTypes
            }
            logger.i("Settings", "Settings saved")
        }
    }
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    application: Application,
    private val database: AppDatabase,
    private val syncRepository: SyncRepository,
    private val logger: Logger
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    init {
        loadLogs()
    }

    private fun loadLogs() {
        viewModelScope.launch {
            val logs = database.eventLogDao().getRecent(500)
            _state.value = _state.value.copy(logs = logs)
        }
    }

    fun refreshLogs() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            loadLogs()
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            database.eventLogDao().deleteAll()
            _state.value = _state.value.copy(logs = emptyList())
            logger.i("Diagnostics", "Logs cleared")
        }
    }

    fun triggerRepairSync(daysBack: Int, recordType: String?) {
        viewModelScope.launch {
            val prefs = application.dataStore.data.first()
            val baseUrl = prefs[Prefs.BASE_URL] ?: ""
            val authToken = prefs[Prefs.AUTH_TOKEN] ?: ""
            val deviceId = prefs[Prefs.DEVICE_ID] ?: ""

            if (baseUrl.isBlank() || authToken.isBlank()) {
                logger.w("Diagnostics", "Missing configuration for repair sync")
                return@launch
            }

            when (val result = syncRepository.performRepairSync(daysBack, recordType, baseUrl, authToken, deviceId)) {
                is Result.Success -> {
                    logger.i("Diagnostics", "Repair sync completed: ${result.data}")
                }
                is Result.Failure -> {
                    logger.e("Diagnostics", "Repair sync failed: ${result.error.message}")
                }
            }
            loadLogs()
        }
    }
}
