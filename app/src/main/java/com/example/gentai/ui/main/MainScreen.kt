package com.warwar13.gentai.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.warwar13.gentai.data.DailySummaryEntity
import com.warwar13.gentai.data.GentaiTelemetryRepository
import com.warwar13.gentai.data.PowerSessionEntity
import com.warwar13.gentai.data.TelemetryDatabase
import com.warwar13.gentai.data.TelemetrySampleEntity
import com.warwar13.gentai.analytics.FlowRecordingMode
import com.warwar13.gentai.analytics.FlowRecordingAnalytics
import com.warwar13.gentai.monitor.GentaiScanner
import com.warwar13.gentai.monitor.ChargingCellPolicy
import com.warwar13.gentai.monitor.ChargingCellSeverity
import com.warwar13.gentai.monitor.ChargingCellStatus
import com.warwar13.gentai.monitor.MonitorService
import com.warwar13.gentai.monitor.MonitorState
import com.warwar13.gentai.monitor.MonitoringPreferences
import com.warwar13.gentai.monitor.estimateBatteryTime
import com.warwar13.gentai.monitor.formatBatteryEstimateClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class TrendMetric(val label: String, val unit: String) {
    POWER("Power", "W"),
    VOLTAGE("Voltage", "V"),
    SOC("SOC", "%"),
    CELL_DELTA("Cell delta", "mV"),
    MOS_TEMP("MOS temp", "°C"),
}

internal enum class GaugeSeverity { GOOD, WARNING, CRITICAL }

internal fun socGaugeSeverity(socPct: Int): GaugeSeverity = when {
    socPct <= 20 -> GaugeSeverity.CRITICAL
    socPct <= 40 -> GaugeSeverity.WARNING
    else -> GaugeSeverity.GOOD
}

internal fun normalizeGaugeValue(value: Double, minimum: Double, maximum: Double): Float {
    if (!value.isFinite() || !minimum.isFinite() || !maximum.isFinite() || maximum <= minimum) return 0f
    return ((value - minimum) / (maximum - minimum)).toFloat().coerceIn(0f, 1f)
}

private val HomeDarkColors = darkColorScheme(
    primary = Color(0xFFA8F05A),
    onPrimary = Color(0xFF142000),
    primaryContainer = Color(0xFF203A18),
    onPrimaryContainer = Color(0xFFD6FFB5),
    secondary = Color(0xFF65D6A6),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF164A3B),
    onSecondaryContainer = Color(0xFF9BF6D1),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF070A08),
    onBackground = Color(0xFFF1F7ED),
    surface = Color(0xFF0E130F),
    onSurface = Color(0xFFF1F7ED),
    surfaceVariant = Color(0xFF202820),
    onSurfaceVariant = Color(0xFFADB8A9),
    outline = Color(0xFF667063),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier, onSettings: () -> Unit = {}, onAutoStartAttempted: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: DashboardViewModel = viewModel {
        DashboardViewModel(GentaiTelemetryRepository(TelemetryDatabase.get(context)))
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeRecordingMode by MonitorService.activeRecordingMode.collectAsStateWithLifecycle()
    val scanner = remember { GentaiScanner(context) }
    val devices by scanner.devices.collectAsStateWithLifecycle()
    val monitoringPreferences = remember { MonitoringPreferences(context) }
    val initialMonitoringState = remember { monitoringPreferences.read() }
    var monitoringRequested by remember { mutableStateOf(initialMonitoringState.requested) }
    var selectedDeviceAddress by remember { mutableStateOf(initialMonitoringState.deviceAddress) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var showTechnicalDetails by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    val effectivelyConnected = isEffectivelyConnected(
        connection = state.connection,
        monitoringRequested = monitoringRequested,
        latestTimestampEpochMs = state.latest?.timestampEpochMs,
    )

    val blePermissions = if (Build.VERSION.SDK_INT >= 31) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissions = (
        blePermissions + if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    ).toTypedArray()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (blePermissions.all { result[it] == true }) scanner.start()
        else showDevicePicker = false
    }

    LaunchedEffect(state.connection) {
        val persisted = monitoringPreferences.read()
        monitoringRequested = persisted.requested
        selectedDeviceAddress = persisted.deviceAddress
        (state.connection as? MonitorState.Connected)?.let { connected ->
            monitoringRequested = connected.recording
            showDevicePicker = false
            scanner.stop()
        }
    }

    DisposableEffect(Unit) { onDispose { scanner.stop() } }

    val openMonitoringSettings: () -> Unit = { onSettings(); Unit }

    MaterialTheme(colorScheme = HomeDarkColors) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            topBar = {
            TopAppBar(
                title = {
                    Text("OyaVolt", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                },
                actions = {
                    val connection = state.connection
                    ConnectionBadge(connection, activeRecordingMode, effectivelyConnected)
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
            },
        ) { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            if (!monitoringRequested && state.connection is MonitorState.Idle) {
                ConnectionControls(
                    connection = state.connection,
                    effectivelyConnected = effectivelyConnected,
                    monitoringRequested = false,
                    hasSelectedBattery = selectedDeviceAddress != null,
                    start = openMonitoringSettings,
                    scan = openMonitoringSettings,
                    stop = openMonitoringSettings,
                )
            }

            if (showDevicePicker) {
                DevicePicker(
                    devices = devices.map { Triple(it.name, it.address, it.rssi) },
                    onConnect = { address ->
                        scanner.stop()
                        showDevicePicker = false
                        selectedDeviceAddress = address
                        onAutoStartAttempted()
                        monitoringRequested = true
                        MonitorService.start(context, address)
                    },
                )
            }

            state.latest?.let { sample ->
                BatteryHeroCard(sample, isLive = effectivelyConnected, connection = state.connection)
                SolarHomeCard()
                HomeEssentialsCard(sample)
                CellBalanceCard(sample)
                TemperatureCard(sample)

                CollapsibleSection(
                    title = "Technical details",
                    subtitle = "Protection flags, signal and raw status",
                    icon = Icons.Rounded.Info,
                    expanded = showTechnicalDetails,
                    onToggle = { showTechnicalDetails = !showTechnicalDetails },
                ) {
                    TechnicalDetails(sample)
                }
            } ?: run {
                EmptyTelemetryCard()
                SolarHomeCard()
            }

            Spacer(Modifier.height(16.dp))
            }
        }
    }

}

@Composable
private fun ConnectionBadge(connection: MonitorState, activeRecordingMode: FlowRecordingMode?, effectivelyConnected: Boolean) {
    val color = when {
        effectivelyConnected -> MaterialTheme.colorScheme.primary
        connection is MonitorState.Connecting || connection is MonitorState.Reconnecting -> MaterialTheme.colorScheme.secondary
        connection is MonitorState.BluetoothUnavailable || connection is MonitorState.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val icon = if (effectivelyConnected) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled
    val status = if (effectivelyConnected && connection is MonitorState.Connected) {
        when (activeRecordingMode) {
            FlowRecordingMode.CHARGING -> "Connected, saving charging"
            FlowRecordingMode.DISCHARGING -> "Connected, saving discharging"
            null -> if (FlowRecordingAnalytics.modeFor(connection.currentA ?: 0.0) == null) "Connected, idle" else "Connected, checking flow"
        }
    } else connectionBadgeText(connection)
    Box(Modifier.size(48.dp).semantics { contentDescription = status }, contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.size(40.dp), color = color.copy(alpha = 0.12f), contentColor = color, shape = CircleShape) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun MonitoringActionButton(active: Boolean, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Surface(modifier = Modifier.size(40.dp), color = color.copy(alpha = 0.14f), contentColor = color, shape = CircleShape) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (active) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = if (active) "Stop monitoring" else "Start monitoring",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectionControls(
    connection: MonitorState,
    effectivelyConnected: Boolean,
    monitoringRequested: Boolean,
    hasSelectedBattery: Boolean,
    start: () -> Unit,
    scan: () -> Unit,
    stop: () -> Unit,
) {
    val primaryStatus = when {
        effectivelyConnected -> "Monitoring is active"
        connection is MonitorState.Connecting -> "Connecting to battery"
        connection is MonitorState.Reconnecting -> "Reconnecting to battery"
        monitoringRequested -> "Monitoring needs to restart"
        hasSelectedBattery -> "Monitoring is stopped"
        else -> "No battery selected"
    }

    val description = when {
        effectivelyConnected -> "Connected and recording telemetry in the background."
        connection is MonitorState.Connecting -> "Connecting to your selected battery…"
        connection is MonitorState.BluetoothUnavailable -> "Bluetooth is off. Turn it on and OyaVolt will retry."
        connection is MonitorState.Reconnecting -> "Connection lost. Retrying in ${connection.delayMs / 1000}s. ${connection.detail}"
        connection is MonitorState.Failed -> connection.detail
        monitoringRequested -> "The selected battery is remembered. Restart monitoring to reconnect."
        hasSelectedBattery -> "Your last battery is remembered. Tap Start monitoring to reconnect without scanning again."
        else -> "Choose your battery once. OyaVolt will remember it for the next start."
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (effectivelyConnected) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
                    contentDescription = null,
                    tint = if (effectivelyConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(primaryStatus, fontWeight = FontWeight.SemiBold)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (monitoringRequested && (effectivelyConnected || connection !is MonitorState.Idle)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = scan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Change battery")
                    }
                    Button(onClick = stop, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Stop monitoring")
                    }
                }
            } else {
                if (hasSelectedBattery) {
                    Button(onClick = start, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start monitoring")
                    }
                    TextButton(onClick = scan, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Choose a different battery")
                    }
                } else {
                    Button(onClick = start, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose battery")
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicePicker(
    devices: List<Triple<String?, String, Int>>,
    onConnect: (String) -> Unit,
) {
    SectionHeader("Choose battery", "Nearby supported Bluetooth BMS devices", Icons.Rounded.Bluetooth)
    if (devices.isEmpty()) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Scanning…", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Keep the battery nearby and Bluetooth enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }
    devices.forEach { (name, address, rssi) ->
        Card(
            onClick = { onConnect(address) },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.BatteryFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name ?: "Unnamed battery", fontWeight = FontWeight.SemiBold)
                    Text(
                        "$rssi dBm • $address",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("Connect", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

internal enum class HomeFlowPresentation { CHARGING, DISCHARGING, IDLE, RECONNECTING, LAST_RECORDED }

internal fun homeFlowPresentation(
    mode: String,
    isLive: Boolean,
    reconnecting: Boolean,
    sampleTimestampMs: Long,
    nowMs: Long,
): HomeFlowPresentation = when {
    reconnecting -> HomeFlowPresentation.RECONNECTING
    // A fresh sample can arrive before the freshness clock's effect updates. Treat that
    // transient negative age as zero so the hero does not flash to "Last recorded".
    !isLive || (nowMs - sampleTimestampMs).coerceAtLeast(0L) > LIVE_TELEMETRY_FRESH_MS -> HomeFlowPresentation.LAST_RECORDED
    mode == "charging" -> HomeFlowPresentation.CHARGING
    mode == "discharging" -> HomeFlowPresentation.DISCHARGING
    else -> HomeFlowPresentation.IDLE
}

@Composable
private fun BatteryHeroCard(sample: TelemetrySampleEntity, isLive: Boolean, connection: MonitorState) {
    val mode = sample.displayMode()
    val now by produceState(System.currentTimeMillis(), sample.timestampEpochMs, isLive, connection) {
        value = System.currentTimeMillis()
        while (true) {
            delay(5_000)
            value = System.currentTimeMillis()
        }
    }
    val presentation = homeFlowPresentation(
        mode,
        isLive,
        connection is MonitorState.Reconnecting || connection is MonitorState.Connecting,
        sample.timestampEpochMs,
        now,
    )
    val fresh = presentation == HomeFlowPresentation.CHARGING ||
        presentation == HomeFlowPresentation.DISCHARGING || presentation == HomeFlowPresentation.IDLE
    val cells = sample.cells().take(4)
    val estimate = if (fresh) estimateTimeLabel(sample) else null
    val chargingCellStatus = ChargingCellPolicy.status(cells).takeIf {
        fresh && ChargingCellPolicy.isCharging(sample.currentA)
    }
    val modeLabel = when (presentation) {
        HomeFlowPresentation.CHARGING -> "Charging"
        HomeFlowPresentation.DISCHARGING -> "Discharging"
        HomeFlowPresentation.IDLE -> "Idle"
        HomeFlowPresentation.RECONNECTING -> "Reconnecting"
        HomeFlowPresentation.LAST_RECORDED -> "Last recorded"
    }
    val modeColor = when (presentation) {
        HomeFlowPresentation.CHARGING -> MaterialTheme.colorScheme.primary
        HomeFlowPresentation.DISCHARGING -> MaterialTheme.colorScheme.tertiary
        HomeFlowPresentation.IDLE -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    val modeIcon = if (presentation == HomeFlowPresentation.CHARGING) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull
    var showFlowInfo by remember { mutableStateOf(false) }

    if (showFlowInfo) {
        AlertDialog(
            onDismissRequest = { showFlowInfo = false },
            title = { Text("Battery energy flow") },
            text = { Text("Solar is the charging source for this setup. Charging power is measured entering the battery. Output is measured leaving the battery, excluding other household loads. Positive current charges; negative current discharges. Moving dots show energy direction. ETA assumes the current stays steady.") },
            confirmButton = { TextButton(onClick = { showFlowInfo = false }) { Text("Close") } },
        )
    }

    val heroShape = RoundedCornerShape(28.dp)
    Card(
        Modifier.fillMaxWidth().border(1.dp, modeColor.copy(alpha = 0.28f), heroShape),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = heroShape,
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            modeColor.copy(alpha = 0.13f),
                            Color(0xFF101A14),
                            Color(0xFF0C140F),
                        ),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = modeColor.copy(alpha = 0.14f),
                    contentColor = modeColor,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(modeIcon, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(modeLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showFlowInfo = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Info, contentDescription = "Explain battery energy flow", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            HomeEnergyTriangle(
                sample = sample,
                presentation = presentation,
            )

            HeroMetricRail(
                power = "${formatNumber(abs(sample.powerW), 0)} W",
                current = signedFormat(sample.currentA, "A"),
                estimate = estimate,
                accent = modeColor,
            )

            chargingCellStatus?.let { ChargingCellStatusBanner(it) }

            if (!fresh) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "Last reading ${formatDateTime(sample.timestampEpochMs)}",
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            sample.gapFromPreviousMs?.takeIf { it > 0 }?.let { gap ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "Recording gap before this sample: ${formatDuration(gap)}",
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        shape = RoundedCornerShape(15.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

private data class HeroMetric(val label: String, val value: String, val secondary: String?, val icon: ImageVector, val accent: Color)

@Composable
private fun HeroMetricRail(power: String, current: String, estimate: EstimateTimeLabel?, accent: Color) {
    val metrics = listOf(
        HeroMetric("Power", power, null, Icons.Rounded.Bolt, accent),
        HeroMetric("Current", current, null, Icons.Rounded.Timeline, accent),
        HeroMetric(estimate?.targetLabel ?: "ETA", estimate?.compactDuration ?: "—", estimate?.targetTime?.let { "by $it" }, Icons.Rounded.Timeline, MaterialTheme.colorScheme.onSurface),
    )
    Surface(color = Color(0xFF17221B), shape = RoundedCornerShape(18.dp)) {
        if (LocalDensity.current.fontScale >= 1.3f) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                metrics.forEachIndexed { index, metric ->
                    HeroMetricContent(metric, Modifier.fillMaxWidth(), reserveSecondaryLine = false)
                    if (index < metrics.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                metrics.forEachIndexed { index, metric ->
                    HeroMetricContent(metric, Modifier.weight(1f))
                    if (index < metrics.lastIndex) VerticalDivider(Modifier.height(42.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.26f))
                }
            }
        }
    }
}

@Composable
private fun HeroMetricContent(metric: HeroMetric, modifier: Modifier, reserveSecondaryLine: Boolean = true) {
    Column(modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(metric.icon, contentDescription = null, tint = metric.accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(metric.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = metric.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (metric.secondary != null || reserveSecondaryLine) {
            Text(metric.secondary ?: " ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
private fun DirectionalFlowStrip(mode: String, isActive: Boolean) {
    val charging = mode == "charging"
    val discharging = mode == "discharging"
    val activeFlow = isActive && (charging || discharging)
    val accent = when {
        charging -> MaterialTheme.colorScheme.primary
        discharging -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    val label = when {
        charging -> "Energy moving into battery"
        discharging -> "Energy moving from battery"
        else -> "Energy flow idle"
    }
    val transition = rememberInfiniteTransition(label = "energy flow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400), RepeatMode.Restart),
        label = "energy flow phase",
    )
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        color = accent.copy(alpha = if (activeFlow) 0.09f else 0.05f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (charging) Icons.Rounded.Bolt else Icons.Rounded.BatteryFull,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp),
            )
            Box(Modifier.weight(1f).height(22.dp).padding(horizontal = 10.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val centerY = size.height / 2f
                    drawLine(
                        color = accent.copy(alpha = if (activeFlow) 0.34f else 0.18f),
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    if (activeFlow) {
                        repeat(5) { index ->
                            val position = (phase + index / 5f) % 1f
                            val x = if (charging) position * size.width else (1f - position) * size.width
                            val wave = (sin((position * 2f * PI).toFloat()) + 1f) / 2f
                            drawCircle(accent.copy(alpha = 0.10f + wave * 0.12f), radius = (7f + wave * 3f).dp.toPx(), center = Offset(x, centerY))
                            drawCircle(accent.copy(alpha = 0.72f + wave * 0.28f), radius = (2.4f + wave * 1.2f).dp.toPx(), center = Offset(x, centerY))
                        }
                        repeat(3) { index ->
                            val x = size.width * (0.28f + index * 0.22f)
                            val direction = if (charging) 1f else -1f
                            val tip = Offset(x + direction * 4.dp.toPx(), centerY)
                            val upper = Offset(x - direction * 2.dp.toPx(), centerY - 4.dp.toPx())
                            val lower = Offset(x - direction * 2.dp.toPx(), centerY + 4.dp.toPx())
                            drawLine(accent.copy(alpha = 0.45f), upper, tip, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(accent.copy(alpha = 0.45f), lower, tip, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        }
                    } else if (charging || discharging) {
                        drawCircle(accent.copy(alpha = 0.45f), radius = 3.dp.toPx(), center = Offset(size.width / 2f, centerY))
                    }
                }
            }
            Icon(
                imageVector = if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.Bolt,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ChargingCellStatusBanner(status: ChargingCellStatus) {
    val color = when (status.severity) {
        ChargingCellSeverity.GREEN -> MaterialTheme.colorScheme.primary
        ChargingCellSeverity.YELLOW -> MaterialTheme.colorScheme.tertiary
        ChargingCellSeverity.RED -> MaterialTheme.colorScheme.error
    }
    val message = when (status.severity) {
        ChargingCellSeverity.GREEN -> "Charging cells within limits"
        ChargingCellSeverity.YELLOW -> "Prepare to stop charging"
        ChargingCellSeverity.RED -> "Unplug charger now"
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null)
            Spacer(Modifier.width(9.dp))
            Column {
                Text(message, fontWeight = FontWeight.Bold)
                Text(
                    "Highest C${status.highCellNumber} ${formatNumber(status.highCellVoltageV, 3)} V • ${formatNumber(status.deltaV * 1_000, 0)} mV spread",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CellVoltageStat(number: Int, voltage: Double?, balanceState: CellBalanceState, modifier: Modifier = Modifier) {
    val color = when (balanceState) {
        CellBalanceState.BALANCED -> MaterialTheme.colorScheme.primary
        CellBalanceState.DIFFERENT -> MaterialTheme.colorScheme.tertiary
        CellBalanceState.ALERT -> MaterialTheme.colorScheme.error
        CellBalanceState.MISSING -> MaterialTheme.colorScheme.outline
    }
    Surface(modifier, color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("C$number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(voltage?.let { formatNumber(it, 3) } ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
            Text("V", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class EstimateTimeLabel(
    val targetLabel: String,
    val compactDuration: String,
    val targetTime: String?,
)

private fun estimateTimeLabel(sample: TelemetrySampleEntity): EstimateTimeLabel? {
    val estimate = estimateBatteryTime(sample.socPct, sample.remainingAh, sample.fullAh, sample.currentA) ?: return null
    val totalMinutes = (estimate.hours * 60.0).toLong().coerceAtLeast(0L)
    val compactDuration = when {
        estimate.hours == 0.0 -> if (estimate.target == "20%") "Now" else "Full"
        totalMinutes >= 60 -> "~${totalMinutes / 60}h ${totalMinutes % 60}m"
        else -> "~${totalMinutes.coerceAtLeast(1)}m"
    }
    return EstimateTimeLabel(
        targetLabel = if (estimate.target == "20%") "To 20%" else "To full",
        compactDuration = compactDuration,
        targetTime = formatBatteryEstimateClock(estimate, sample.timestampEpochMs, ZoneId.systemDefault()),
    )
}


@Composable
private fun HomeEssentialsCard(sample: TelemetrySampleEntity) {
    val maximumTemperature = (sample.probes() + sample.ambientTemperatureC + sample.mosTemperatureC).maxOrNull()
    val temperatureColor = maximumTemperature?.takeIf { it >= 45 }?.let { temperatureMeterColor(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Battery essentials", "Pack health at a glance", Icons.Rounded.Speed)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
        ) {
            DividedMetricGrid(
                listOf(
                    HomeMetricLine("Pack voltage", "${formatNumber(sample.voltageV, 2)} V", Icons.Rounded.Bolt),
                    HomeMetricLine("Remaining", "${formatNumber(sample.remainingAh, 2)} Ah", Icons.Rounded.BatteryFull),
                    HomeMetricLine("Health", "${sample.sohPct}%", Icons.Rounded.BatteryFull),
                    HomeMetricLine("Max temp", maximumTemperature?.let { "$it °C" } ?: "N/A", Icons.Rounded.Thermostat, temperatureColor),
                ),
            )
        }
    }
}

private data class HomeMetricLine(val label: String, val value: String, val icon: ImageVector? = null, val valueColor: Color? = null)

@Composable
private fun DividedMetricGrid(metrics: List<HomeMetricLine>) {
    val stacked = LocalDensity.current.fontScale >= 1.3f
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        metrics.chunked(if (stacked) 1 else 2).forEachIndexed { rowIndex, rowMetrics ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                rowMetrics.forEachIndexed { index, metric ->
                    HomeMetricCell(metric, Modifier.weight(1f))
                    if (index < rowMetrics.lastIndex) VerticalDivider(Modifier.height(42.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                }
                if (!stacked && rowMetrics.size == 1) Spacer(Modifier.weight(1f))
            }
            if (rowIndex < (metrics.size - 1) / (if (stacked) 1 else 2)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            }
        }
    }
}

@Composable
private fun HomeMetricCell(metric: HomeMetricLine, modifier: Modifier) {
    Column(modifier.heightIn(min = 68.dp).padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            metric.icon?.let {
                Icon(it, contentDescription = null, tint = metric.valueColor ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(metric.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(metric.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = metric.valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DashboardMeters(sample: TelemetrySampleEntity) {
    val maximumTemperature = (sample.probes() + sample.ambientTemperatureC + sample.mosTemperatureC).maxOrNull() ?: sample.mosTemperatureC
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.padding(8.dp).size(19.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("Live instruments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Electrical and thermal load", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AnalogMeter("PACK", sample.voltageV, 10.0, 15.0, "${formatNumber(sample.voltageV, 2)} V", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                AnalogMeter("LOAD", abs(sample.powerW), 0.0, 600.0, "${formatNumber(abs(sample.powerW), 0)} W", loadMeterColor(abs(sample.powerW)), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AnalogMeter("TEMPERATURE", maximumTemperature.toDouble(), 0.0, 65.0, "$maximumTemperature °C", temperatureMeterColor(maximumTemperature), Modifier.weight(1f))
                AnalogMeter("CURRENT", abs(sample.currentA), 0.0, 50.0, signedFormat(sample.currentA, "A"), loadMeterColor(abs(sample.powerW)), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnalogMeter(
    title: String,
    value: Double,
    minimum: Double,
    maximum: Double,
    display: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val targetProgress = normalizeGaugeValue(value, minimum, maximum)
    val progress by animateFloatAsState(targetProgress, tween(550), label = "$title gauge")
    Surface(
        modifier = modifier.semantics { contentDescription = "$title $display" },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                val center = Offset(size.width / 2f, size.height * 0.92f)
                val radius = min(size.width * 0.39f, size.height * 0.76f)
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2f, radius * 2f)
                drawArc(trackColor, 150f, 240f, false, topLeft, arcSize, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color.copy(alpha = 0.18f), 150f, 240f * progress, false, topLeft, arcSize, style = Stroke(13.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color, 150f, 240f * progress, false, topLeft, arcSize, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                repeat(13) { index ->
                    val angle = (150.0 + 240.0 * index / 12.0) * PI / 180.0
                    val outerRadius = radius + 10.dp.toPx()
                    val innerRadius = outerRadius - if (index % 3 == 0) 6.dp.toPx() else 3.dp.toPx()
                    drawLine(
                        if (index / 12f <= progress) color else trackColor,
                        Offset(center.x + cos(angle).toFloat() * innerRadius, center.y + sin(angle).toFloat() * innerRadius),
                        Offset(center.x + cos(angle).toFloat() * outerRadius, center.y + sin(angle).toFloat() * outerRadius),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                val angle = (150.0 + 240.0 * progress) * PI / 180.0
                val needle = Offset(center.x + cos(angle).toFloat() * radius * 0.72f, center.y + sin(angle).toFloat() * radius * 0.72f)
                drawLine(color, center, needle, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color, radius = 4.5.dp.toPx(), center = center)
            }
            Text(display, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${formatNumber(minimum, 0)}–${formatNumber(maximum, 0)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable private fun loadMeterColor(powerW: Double): Color = when {
    powerW >= 600 -> MaterialTheme.colorScheme.error
    powerW >= 400 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable private fun temperatureMeterColor(temperatureC: Int): Color = when {
    temperatureC >= 55 -> MaterialTheme.colorScheme.error
    temperatureC >= 45 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable private fun cellMeterColor(deltaMv: Double): Color = when {
    deltaMv >= 80 -> MaterialTheme.colorScheme.error
    deltaMv >= 30 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun PrimaryMetrics(sample: TelemetrySampleEntity) {
    SectionHeader("Electrical", "The three values you will use most", Icons.Rounded.Bolt)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Voltage", format(sample.voltageV, "V"), "Pack", Modifier.weight(1f))
        MetricCard("Current", signedFormat(sample.currentA, "A"), currentDirection(sample.currentA), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Power", signedFormat(sample.powerW, "W"), powerDirection(sample.powerW), Modifier.weight(1f))
        MetricCard("Signal", sample.rssi?.let { "$it dBm" } ?: "—", "Bluetooth", Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(title: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun cellOffsetFraction(cellV: Double, averageV: Double): Float =
    (((cellV - averageV) * 1000.0) / 25.0).toFloat().coerceIn(-1f, 1f)

@Composable
private fun CellBalanceCard(sample: TelemetrySampleEntity) {
    val cells = sample.cells()
    if (cells.isEmpty()) return
    val cellMin = cells.minOrNull() ?: return
    val cellMax = cells.maxOrNull() ?: return
    val average = cells.average()
    val deltaMv = (cellMax - cellMin) * 1000.0
    val balanceColor = cellMeterColor(deltaMv)
    val cellTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val cellCenterColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Cell balance", "Spread ${deltaMv.roundToInt()} mV across ${cells.size} cells", Icons.Rounded.BatteryFull)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cells.forEachIndexed { index, voltage ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("C${index + 1}", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Canvas(Modifier.weight(1f).height(18.dp)) {
                            val center = size.width / 2f
                            drawLine(cellTrackColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 2.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(cellCenterColor, Offset(center, 0f), Offset(center, size.height), 1.dp.toPx())
                            drawCircle(balanceColor, 4.dp.toPx(), Offset(center + cellOffsetFraction(voltage, average) * center, size.height / 2f))
                        }
                        Text("${(voltage - average).times(1000.0).roundToInt().let { if (it > 0) "+$it" else "$it" }} mV", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatNumber(voltage, 3)} V", modifier = Modifier.width(68.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                val summary = listOf(
                    HomeMetricLine("Lowest", "${formatNumber(cellMin, 3)} V"),
                    HomeMetricLine("Highest", "${formatNumber(cellMax, 3)} V"),
                    HomeMetricLine("Delta", "${deltaMv.roundToInt()} mV", valueColor = if (deltaMv >= 30) balanceColor else null),
                )
                if (LocalDensity.current.fontScale >= 1.3f) {
                    DividedMetricGrid(summary)
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        summary.forEachIndexed { index, metric ->
                            HomeMetricCell(metric, Modifier.weight(1f))
                            if (index < summary.lastIndex) VerticalDivider(Modifier.height(42.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemperatureCard(sample: TelemetrySampleEntity) {
    val sensors = listOf("MOS" to sample.mosTemperatureC, "Ambient" to sample.ambientTemperatureC) +
        sample.probes().mapIndexed { index, temperature -> "Probe ${index + 1}" to temperature }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Temperatures", "Battery and controller sensors", Icons.Rounded.Thermostat)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(20.dp)) {
            DividedMetricGrid(sensors.map { (name, temperature) ->
                HomeMetricLine(name, "$temperature °C", valueColor = temperature.takeIf { it >= 45 }?.let { temperatureMeterColor(it) })
            })
        }
    }
}

@Composable
private fun TrendCard(samples: List<TelemetrySampleEntity>) {
    var selected by remember { mutableStateOf(TrendMetric.POWER) }
    val values = remember(samples, selected) {
        when (selected) {
            TrendMetric.POWER -> samples.map { it.powerW }
            TrendMetric.VOLTAGE -> samples.map { it.voltageV }
            TrendMetric.SOC -> samples.map { it.socPct.toDouble() }
            TrendMetric.CELL_DELTA -> samples.mapNotNull { it.cellDeltaV?.times(1000) }
            TrendMetric.MOS_TEMP -> samples.map { it.mosTemperatureC.toDouble() }
        }
    }

    SectionHeader("Recent trends", "Swipe the dashboard to review the latest recorded samples", Icons.Rounded.Timeline)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrendMetric.entries.take(3).forEach { metric ->
                    FilterChip(
                        selected = selected == metric,
                        onClick = { selected = metric },
                        label = { Text(metric.label) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrendMetric.entries.drop(3).forEach { metric ->
                    FilterChip(
                        selected = selected == metric,
                        onClick = { selected = metric },
                        label = { Text(metric.label) },
                    )
                }
            }

            if (values.size >= 2) {
                val minValue = values.minOrNull() ?: 0.0
                val maxValue = values.maxOrNull() ?: 0.0
                val latest = values.last()
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(selected.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${formatNumber(latest, if (selected == TrendMetric.VOLTAGE) 2 else 1)} ${selected.unit}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "${formatNumber(minValue, 1)} – ${formatNumber(maxValue, 1)} ${selected.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledTrendChart(values, selected.unit)
                Text(
                    "${samples.size} recent samples • oldest → newest",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("More samples are needed before a trend can be drawn.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun LabeledTrendChart(values: List<Double>, unit: String) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val maxValue = values.maxOrNull() ?: return
    val minValue = values.minOrNull() ?: return
    val range = (maxValue - minValue).takeIf { it > 0.000001 } ?: 1.0

    Column {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                repeat(4) { row ->
                    val y = size.height * row / 3f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = if (values.size == 1) 0f else size.width * index / (values.size - 1)
                    val y = size.height - (((value - minValue) / range) * size.height).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                val lastY = size.height - (((values.last() - minValue) / range) * size.height).toFloat()
                drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(size.width, lastY))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${formatNumber(minValue, 1)} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${formatNumber(maxValue, 1)} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
            }
        }
    }
}

@Composable
private fun TechnicalDetails(sample: TelemetrySampleEntity) {
    val rows = listOf(
        "Protection flags" to (sample.protectionBitfield?.let { "0x${it.toString(16).uppercase()}" } ?: "Not received"),
        "Balance mask" to (sample.balanceMask?.let { "0x${it.toString(16).uppercase()}" } ?: "—"),
        "Disconnected cells" to (sample.disconnectedCellMask?.toString() ?: "—"),
        "Quality flags" to "0x${sample.qualityFlags.toString(16).uppercase()}",
        "Bluetooth signal" to (sample.rssi?.let { "$it dBm" } ?: "—"),
        "Last sample" to formatDateTime(sample.timestampEpochMs),
    )
    rows.forEachIndexed { index, (label, value) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Medium)
        }
        if (index != rows.lastIndex) HorizontalDivider()
    }
}

@Composable
internal fun SessionRow(session: PowerSessionEntity) {
    val isCharge = session.mode.equals("CHARGING", ignoreCase = true)
    val color = if (isCharge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val energy = if (isCharge) session.chargedWh else session.dischargedWh
    val socText = if (session.startSocPct != null && session.endSocPct != null) {
        "${session.startSocPct}% → ${session.endSocPct}%"
    } else {
        "SOC unavailable"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isCharge) Icons.Rounded.BatteryChargingFull else Icons.Rounded.Bolt,
                contentDescription = null,
                tint = color,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isCharge) "Charging" else "Discharging", fontWeight = FontWeight.SemiBold)
            Text(
                "${formatDateTime(session.startEpochMs)} • ${formatDuration(session.endEpochMs - session.startEpochMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$socText • ${formatNumber(session.coveragePct, 0)}% coverage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${formatNumber(energy, 1)} Wh", fontWeight = FontWeight.Bold)
            Text("peak ${formatNumber(session.peakAbsolutePowerW, 0)} W", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DailyRow(day: DailySummaryEntity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formatDay(day.dayStartEpochMs), fontWeight = FontWeight.Medium)
            Text("${formatNumber(day.coveragePct, 0)}% data coverage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Out ${formatNumber(day.dischargedWh, 1)} Wh", style = MaterialTheme.typography.bodySmall)
            Text("In ${formatNumber(day.chargedWh, 1)} Wh", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsCard(resumeAfterRestart: Boolean, onResumeChange: (Boolean) -> Unit) {
    SectionHeader("Monitoring settings", "Background reliability on your Poco", Icons.Rounded.Settings)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = resumeAfterRestart, onCheckedChange = onResumeChange)
                Column(Modifier.weight(1f)) {
                    Text("Resume after phone restart", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Only resumes monitoring when you explicitly enable this option.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "For reliable screen-off logging on POCO/Xiaomi, allow Autostart and set OyaVolt battery use to No restrictions. Recording gaps remain visible if Android stops the process.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTelemetryCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("Ready to monitor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Select a battery to see live power, cells and temperatures.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String?, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal const val LIVE_TELEMETRY_FRESH_MS = 20_000L

internal fun isEffectivelyConnected(
    connection: MonitorState,
    monitoringRequested: Boolean,
    latestTimestampEpochMs: Long?,
    nowEpochMs: Long = System.currentTimeMillis(),
): Boolean {
    if (connection is MonitorState.Connected) return true
    if (connection !is MonitorState.Idle || !monitoringRequested || latestTimestampEpochMs == null) return false
    val ageMs = nowEpochMs - latestTimestampEpochMs
    return ageMs in 0..LIVE_TELEMETRY_FRESH_MS
}

internal fun shouldAnimateHomeFlow(
    mode: String,
    isLive: Boolean,
    sampleTimestampEpochMs: Long,
    nowEpochMs: Long,
): Boolean {
    if (!isLive || mode !in setOf("charging", "discharging")) return false
    val ageMs = (nowEpochMs - sampleTimestampEpochMs).coerceAtLeast(0L)
    return ageMs <= LIVE_TELEMETRY_FRESH_MS
}

private fun connectionBadgeText(state: MonitorState): String = when (state) {
    MonitorState.Idle -> "Stopped"
    MonitorState.Connecting -> "Connecting"
    MonitorState.BluetoothUnavailable -> "Bluetooth off"
    is MonitorState.Connected -> "Connected"
    is MonitorState.Reconnecting -> "Reconnecting"
    is MonitorState.Failed -> "Needs attention"
}

private fun connectionDescription(state: MonitorState): String = when (state) {
    MonitorState.Idle -> "Not monitoring. Scan to choose a battery."
    MonitorState.Connecting -> "Connecting to the selected battery…"
    MonitorState.BluetoothUnavailable -> "Bluetooth is off. OyaVolt will wait for it to become available."
    is MonitorState.Connected -> "Connected and recording telemetry in the background."
    is MonitorState.Reconnecting -> "Connection lost. Retrying in ${state.delayMs / 1000}s. ${state.detail}"
    is MonitorState.Failed -> state.detail
}

private fun format(value: Double, unit: String): String = "${formatNumber(value, 2)} $unit"
private fun signedFormat(value: Double, unit: String): String = "%+.2f %s".format(value, unit)
private fun formatNumber(value: Double, decimals: Int): String = "%.${decimals}f".format(value)

private fun currentDirection(value: Double): String = when {
    value > 0.05 -> "Charging current"
    value < -0.05 -> "Discharge current"
    else -> "Near zero"
}

private fun powerDirection(value: Double): String = when {
    value > 0.5 -> "Into battery"
    value < -0.5 -> "From battery"
    else -> "Near zero"
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

private fun formatDateTime(epochMs: Long): String = Instant.ofEpochMilli(epochMs)
    .atZone(ZoneId.systemDefault())
    .format(dateTimeFormatter)

private fun formatDay(epochMs: Long): String = Instant.ofEpochMilli(epochMs)
    .atZone(ZoneId.systemDefault())
    .format(dayFormatter)
