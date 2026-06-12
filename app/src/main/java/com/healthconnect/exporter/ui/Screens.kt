package com.healthconnect.exporter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthconnect.exporter.storage.STATUS_ACKNOWLEDGED
import com.healthconnect.exporter.storage.STATUS_FAILED
import com.healthconnect.exporter.storage.STATUS_IN_FLIGHT
import com.healthconnect.exporter.storage.STATUS_PENDING
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val allRecordTypes = listOf(
    "StepsRecord" to "Steps",
    "HeartRateRecord" to "Heart Rate",
    "SleepSessionRecord" to "Sleep",
    "DistanceRecord" to "Distance",
    "ActiveCaloriesBurnedRecord" to "Active Calories",
    "WeightRecord" to "Weight",
    "BloodPressureRecord" to "Blood Pressure",
    "WorkoutRecord" to "Workouts"
)

// ─── Onboarding Screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Health Connect Exporter") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Welcome",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This app exports your Health Connect data to a self-hosted hcgateway endpoint. " +
                            "Data is stored locally and synced when a network connection is available.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                val availColor = if (state.isHealthConnectAvailable)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                val availText = if (state.isHealthConnectAvailable)
                    "Health Connect available" else "Health Connect not available"
                val availIcon = if (state.isHealthConnectAvailable)
                    Icons.Default.CheckCircle else Icons.Default.Error

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(availIcon, availText, tint = availColor, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(availText, color = availColor, fontWeight = FontWeight.Medium)
                }
            }

            item {
                val permColor = if (state.hasPermissions)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                val permText = if (state.hasPermissions)
                    "Permissions granted" else "Permissions not yet granted"
                val permIcon = if (state.hasPermissions)
                    Icons.Default.CheckCircle else Icons.Default.Warning

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(permIcon, permText, tint = permColor, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(permText, color = permColor, fontWeight = FontWeight.Medium)
                }

                if (!state.hasPermissions) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.checkPermissions() }) {
                        Text("Check Permissions")
                    }
                }
            }

            item {
                Text(
                    "Select data types to export",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(allRecordTypes) { (type, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.selectedTypes.contains(type),
                        onCheckedChange = { viewModel.toggleType(type) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveOnboarding() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.selectedTypes.isNotEmpty()
                ) {
                    Text("Continue", fontWeight = FontWeight.Bold)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ─── Dashboard Screen ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Sync warning
            if (state.syncWarning != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                state.syncWarning!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Sync button
            item {
                Button(
                    onClick = { viewModel.triggerSync() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSyncing
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Syncing...")
                    } else {
                        Text("Trigger Sync", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Queue stats
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Queue Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem("Pending", state.pendingCount, STATUS_PENDING_COLOR)
                            StatItem("In Flight", state.inFlightCount, STATUS_IN_FLIGHT_COLOR)
                            StatItem("Synced", state.acknowledgedCount, STATUS_ACKNOWLEDGED_COLOR)
                            StatItem("Failed", state.failedCount, STATUS_FAILED_COLOR)
                        }
                    }
                }
            }

            // Cursors
            if (state.cursors.isNotEmpty()) {
                item {
                    Text(
                        "Sync Cursors",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(state.cursors) { cursor ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(cursor.recordType, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Watermark: ${formatTimestamp(cursor.highWatermarkMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Last sync: ${cursor.lastSuccessfulSyncAt?.let { formatTimestamp(it) } ?: "Never"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun StatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val STATUS_PENDING_COLOR = Color(0xFFFF9800)
private val STATUS_IN_FLIGHT_COLOR = Color(0xFF2196F3)
private val STATUS_ACKNOWLEDGED_COLOR = Color(0xFF4CAF50)
private val STATUS_FAILED_COLOR = Color(0xFFF44336)

// ─── Settings Screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Text(
                    "Gateway Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = { viewModel.updateBaseUrl(it) },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://your-hcgateway.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = state.authToken,
                    onValueChange = { viewModel.updateAuthToken(it) },
                    label = { Text("Authorization Token") },
                    placeholder = { Text("Bearer your-token-here") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = state.deviceId,
                    onValueChange = { viewModel.updateDeviceId(it) },
                    label = { Text("Device ID") },
                    placeholder = { Text("my-android-phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Data Types",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(allRecordTypes) { (type, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.enabledTypes.contains(type),
                        onCheckedChange = { viewModel.toggleType(type) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Settings", fontWeight = FontWeight.Bold)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ─── Diagnostics Screen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showRepairDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Logs") },
            text = { Text("This will permanently remove all diagnostic logs. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLogs()
                    showClearDialog = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRepairDialog) {
        RepairSyncDialog(
            onDismiss = { showRepairDialog = false },
            onConfirm = { days, type ->
                viewModel.triggerRepairSync(days, type)
                showRepairDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                actions = {
                    IconButton(onClick = { viewModel.refreshLogs() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Logs")
                }
                FilledTonalButton(
                    onClick = { showRepairDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Repair Sync")
                }
            }
        }
    ) { padding ->
        if (state.logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No logs yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        "${state.logs.size} entries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(state.logs) { entry ->
                    LogEntryRow(entry)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: com.healthconnect.exporter.storage.AppEventLogEntity) {
    val levelColor = when (entry.level) {
        "ERROR" -> MaterialTheme.colorScheme.error
        "WARNING" -> Color(0xFFFF9800)
        "INFO" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    entry.level,
                    color = levelColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    formatTimestamp(entry.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                entry.message,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepairSyncDialog(
    onDismiss: () -> Unit,
    onConfirm: (daysBack: Int, recordType: String?) -> Unit
) {
    var daysBack by remember { mutableIntStateOf(7) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repair Sync") },
        text = {
            Column {
                Text("Re-sync records from a lookback window.")
                Spacer(Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = "${daysBack} days",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Lookback Range") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                        },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(1, 3, 7, 14, 30).forEach { days ->
                            DropdownMenuItem(
                                text = { Text("$days days") },
                                onClick = {
                                    daysBack = days
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedType == null,
                        onCheckedChange = { selectedType = null }
                    )
                    Text("All types")
                }
                allRecordTypes.take(4).forEach { (type, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedType == type,
                            onCheckedChange = { selectedType = type }
                        )
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(daysBack, selectedType) }) {
                Text("Start Repair")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
