package com.warwar13.gentai.ui.main

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warwar13.gentai.analytics.EnergyPreferences
import com.warwar13.gentai.data.GentaiTelemetryRepository
import com.warwar13.gentai.data.TelemetryDatabase
import com.warwar13.gentai.monitor.MonitoringPreferences
import com.warwar13.gentai.monitor.GentaiScanner
import com.warwar13.gentai.monitor.MonitorService
import com.warwar13.gentai.monitor.MonitorState
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private class SolarSettingsViewModel(
    private val preferences: EnergyPreferences,
    private val repository: GentaiTelemetryRepository,
) : ViewModel() {
    var message by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var resetting by mutableStateOf(false)
        private set
    var keepingToday by mutableStateOf(false)
        private set

    fun save(tariff: String, cost: String, startDate: String, storage: String) {
        if (saving || resetting || keepingToday) return
        val price = tariff.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val investment = cost.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val capacity = storage.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val date = runCatching { LocalDate.parse(startDate.trim()) }.getOrNull()
        if ((tariff.isNotBlank() && price == null) || (cost.isNotBlank() && investment == null) || (storage.isNotBlank() && capacity == null) || date == null) {
            message = "Enter valid numbers and a YYYY-MM-DD start date."
            return
        }
        saving = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val previous = preferences.settings.value.trackingStartMs
                val updated = preferences.save(price, investment, date, capacity)
                if (previous != updated.trackingStartMs) {
                    repository.rebuildSolarRollups(updated)
                    preferences.markRebuilt(updated.trackingStartMs)
                }
                message = "Settings saved"
            } catch (error: Exception) {
                message = error.message ?: "Could not save settings"
            } finally {
                saving = false
            }
        }
    }

    fun resetRecordedData() {
        if (saving || resetting || keepingToday) return
        resetting = true
        message = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteTelemetryHistory(preferences)
                message = "Recorded data reset. New samples will start a fresh solar history."
            } catch (error: Exception) {
                message = error.message ?: "Could not reset recorded data"
            } finally {
                resetting = false
            }
        }
    }

    fun deleteOlderDataKeepToday() {
        if (saving || resetting || keepingToday) return
        keepingToday = true
        message = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteTelemetryBeforeKeepingToday(preferences)
                message = "Older data deleted. Today's recordings were kept."
            } catch (error: Exception) {
                message = error.message ?: "Could not delete older data"
            } finally {
                keepingToday = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolarSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { EnergyPreferences.get(context) }
    val monitoringPreferences = remember { MonitoringPreferences(context) }
    val scanner = remember { GentaiScanner(context) }
    val discoveredDevices by scanner.devices.collectAsStateWithLifecycle()
    val monitorState by MonitorService.state.collectAsStateWithLifecycle()
    val lastSavedAt by MonitorService.lastSavedAtEpochMs.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { nowMs = System.currentTimeMillis(); delay(5_000) }
        }
    }
    val viewModel: SolarSettingsViewModel = viewModel {
        SolarSettingsViewModel(preferences, GentaiTelemetryRepository(TelemetryDatabase.get(context)))
    }
    val settings by preferences.settings.collectAsStateWithLifecycle()
    var tariff by remember { mutableStateOf(settings.tariffPerKwh?.toString() ?: "") }
    var cost by remember { mutableStateOf(settings.systemCost?.toString() ?: "") }
    var storage by remember { mutableStateOf(settings.ratedStorageKwh?.toString() ?: "") }
    var startDate by remember { mutableStateOf(Instant.ofEpochMilli(settings.trackingStartMs).atZone(settings.zone).toLocalDate().toString()) }
    var resumeAfterRestart by remember { mutableStateOf(monitoringPreferences.read().resumeAfterRestart) }
    var monitoringRequested by remember { mutableStateOf(monitoringPreferences.read().requested) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var pendingMonitoringStart by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmKeepToday by remember { mutableStateOf(false) }

    LaunchedEffect(settings.trackingStartMs) {
        startDate = Instant.ofEpochMilli(settings.trackingStartMs).atZone(settings.zone).toLocalDate().toString()
    }

    LaunchedEffect(monitorState) {
        monitoringRequested = monitoringPreferences.read().requested
    }

    val blePermissions = if (Build.VERSION.SDK_INT >= 31) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (blePermissions.all { result[it] == true }) {
            scanner.start()
        } else {
            pendingMonitoringStart = false
            showDevicePicker = false
            monitoringRequested = false
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { scanner.stop() }
    }

    fun beginMonitoring() {
        val state = monitoringPreferences.read()
        val hasPermissions = blePermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!hasPermissions) {
            pendingMonitoringStart = true
            showDevicePicker = true
            permissionLauncher.launch(blePermissions.toTypedArray())
            return
        }
        val address = state.deviceAddress
        if (address == null) {
            pendingMonitoringStart = true
            showDevicePicker = true
            scanner.start()
        } else {
            monitoringRequested = true
            MonitorService.start(context, address)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset recorded data?") },
            text = { Text("This permanently deletes recorded battery samples, completed sessions, and energy totals, including old wall-charging history. Capacity tests and alert history stay. Monitoring will keep recording new samples, so reset when you switch to solar. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    viewModel.resetRecordedData()
                }) { Text("Reset data") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }

    if (confirmKeepToday) {
        AlertDialog(
            onDismissRequest = { confirmKeepToday = false },
            title = { Text("Delete older data, keep today?") },
            text = { Text("This deletes telemetry, sessions, summaries and flow recordings before the start of today in your reporting time zone. Today's recordings and samples stay. Stop monitoring first if you want a clean cutoff. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    confirmKeepToday = false
                    viewModel.deleteOlderDataKeepToday()
                }) { Text("Delete older data") }
            },
            dismissButton = { TextButton(onClick = { confirmKeepToday = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Energy and savings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Currency: ₱ Philippine peso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(tariff, onValueChange = { tariff = it }, label = { Text("Value per kWh") }, supportingText = { Text("Used for savings estimates; default is ₱13.00/kWh") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cost, onValueChange = { cost = it }, label = { Text("Total solar setup cost") }, supportingText = { Text("Include the equipment cost you want to recover") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(storage, onValueChange = { storage = it }, label = { Text("Rated battery storage (kWh)") }, supportingText = { Text("Optional label; measured energy still comes from the BMS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(startDate, onValueChange = { startDate = it }, label = { Text("Solar tracking starts") }, supportingText = { Text("YYYY-MM-DD; older recordings remain in History") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Reporting time zone: ${settings.reportingZoneId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { viewModel.save(tariff, cost, startDate, storage) }, enabled = !viewModel.saving && !viewModel.resetting) { Text(if (viewModel.saving) "Saving…" else "Save energy settings") }
                    viewModel.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Monitoring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Continuous monitoring", fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    monitorState is MonitorState.Failed -> (monitorState as MonitorState.Failed).detail
                                    monitorState is MonitorState.BluetoothUnavailable -> "Bluetooth is off; recording will resume when enabled."
                                    monitorState is MonitorState.Connected && (lastSavedAt == null || nowMs - checkNotNull(lastSavedAt) > 30_000) -> "Connected, but no recently saved reading. Check recording."
                                    monitorState is MonitorState.Connected -> "Connected; saving BMS readings."
                                    monitorState is MonitorState.Reconnecting || monitorState is MonitorState.Connecting -> "Trying to reconnect; missing time will remain visible."
                                    monitoringRequested -> "Monitoring is enabled but no recent reading is available."
                                    else -> "Off. Enable this to record charging, discharging, and idle readings."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = monitoringRequested, onCheckedChange = { enabled ->
                            if (enabled) beginMonitoring() else {
                                monitoringRequested = false
                                pendingMonitoringStart = false
                                showDevicePicker = false
                                scanner.stop()
                                MonitorService.stop(context)
                            }
                        })
                    }
                    lastSavedAt?.let { timestamp ->
                        val time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm:ss"))
                        Text("Last saved to phone: $time", style = MaterialTheme.typography.bodySmall)
                    }
                    if (showDevicePicker) {
                        Text("Choose a supported BMS", fontWeight = FontWeight.SemiBold)
                        if (discoveredDevices.isEmpty()) {
                            Text("Scanning… keep the battery nearby and Bluetooth enabled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        discoveredDevices.forEach { device ->
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    scanner.stop()
                                    monitoringPreferences.deviceSelected(device.address)
                                    showDevicePicker = false
                                    pendingMonitoringStart = false
                                    monitoringRequested = true
                                    MonitorService.start(context, device.address)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("${device.name}  •  ${device.rssi} dBm") }
                        }
                    }
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = resumeAfterRestart, onCheckedChange = { resumeAfterRestart = it; monitoringPreferences.setResumeAfterRestart(it) })
                        Text("Resume monitoring after phone restart", modifier = Modifier.padding(top = 12.dp))
                    }
                    Text("Screen-off recording keeps the CPU available for five-second BLE polling. This uses extra phone battery; it stops when monitoring is off or Bluetooth is disabled. Charging, discharging, and idle readings are saved; interruptions remain visible as gaps.", style = MaterialTheme.typography.bodySmall)
                    BackgroundRecordingSetup()
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recorded data cleanup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Already have a useful recording today? Remove older wall-era data while keeping today's samples.", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.OutlinedButton(
                        onClick = { confirmKeepToday = true },
                        enabled = !viewModel.saving && !viewModel.resetting && !viewModel.keepingToday,
                    ) { Text(if (viewModel.keepingToday) "Deleting older data..." else "Delete older data, keep today") }
                    Text("Use this when you begin solar charging. It clears old telemetry and energy totals, then starts a new solar tracking period. Capacity tests and alerts are kept.", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { confirmReset = true },
                        enabled = !viewModel.saving && !viewModel.resetting && !viewModel.keepingToday,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(if (viewModel.resetting) "Resetting…" else "Reset recorded data") }
                }
            }
            Text("The BMS sees net battery flow only. Charging is assumed solar; wall charging and direct solar-to-load power cannot be detected. Savings use measured battery discharge × your current grid price.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
