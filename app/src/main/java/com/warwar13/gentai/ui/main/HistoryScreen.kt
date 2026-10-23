package com.warwar13.gentai.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import android.app.Activity
import com.warwar13.gentai.data.DailySummaryEntity
import com.warwar13.gentai.data.SolarDailyRollupEntity
import com.warwar13.gentai.data.FlowRecordingEntity
import com.warwar13.gentai.data.TelemetrySampleEntity
import com.warwar13.gentai.data.RecordingChartPoint
import com.warwar13.gentai.data.GentaiTelemetryRepository
import com.warwar13.gentai.data.RecordingHistoryItem
import com.warwar13.gentai.data.TelemetryChartPoint
import com.warwar13.gentai.data.TelemetryDatabase
import com.warwar13.gentai.analytics.EnergyPreferences
import com.warwar13.gentai.analytics.EnergySettings
import com.warwar13.gentai.analytics.ReadableTrend
import com.warwar13.gentai.analytics.TrendReading
import com.warwar13.gentai.capacity.CapacityTestRepository
import com.warwar13.gentai.capacity.CapacityTestSummary
import java.io.Reader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val HistoryDarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFA8F05A),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF142000),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF203A18),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD6FFB5),
    secondary = androidx.compose.ui.graphics.Color(0xFF65D6A6),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF00382A),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF164A3B),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF9BF6D1),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFFC857),
    background = androidx.compose.ui.graphics.Color(0xFF070A08),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF1F7ED),
    surface = androidx.compose.ui.graphics.Color(0xFF0E130F),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF1F7ED),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF202820),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFADB8A9),
    outline = androidx.compose.ui.graphics.Color(0xFF667063),
    error = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
)

internal enum class HistoryRange(val label: String) {
    DAY("Today"),
    WEEK("7 days"),
    MONTH("This month"),
}

internal enum class HistoryDirectionFilter(val label: String) {
    ALL("All"),
    CHARGING("Charging"),
    DISCHARGING("Discharging"),
    ;
    val mode: String? get() = when (this) { ALL -> null; CHARGING -> "CHARGING"; DISCHARGING -> "DISCHARGING" }
}

internal data class HistoryWindow(val startEpochMs: Long, val endEpochMs: Long, val bucketMs: Long)

internal data class HistorySelection(val range: HistoryRange?, val window: HistoryWindow)

internal fun HistoryRange.window(nowEpochMs: Long, zoneId: ZoneId): HistoryWindow {
    val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
    val first = when (this) {
        HistoryRange.DAY -> today
        HistoryRange.WEEK -> today.minusDays(6)
        HistoryRange.MONTH -> today.withDayOfMonth(1)
    }
    val start = first.atStartOfDay(zoneId).toInstant().toEpochMilli()
    return HistoryWindow(start, nowEpochMs, historyBucketFor(nowEpochMs - start + 1))
}

internal fun selectedHistoryDatesWindow(
    pickerStartEpochMs: Long,
    pickerEndEpochMs: Long,
    zoneId: ZoneId,
): HistoryWindow {
    val startDate = Instant.ofEpochMilli(pickerStartEpochMs).atZone(ZoneOffset.UTC).toLocalDate()
    val endDate = Instant.ofEpochMilli(pickerEndEpochMs).atZone(ZoneOffset.UTC).toLocalDate().coerceAtLeast(startDate)
    val start = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val end = endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    return HistoryWindow(start, end, historyBucketFor(end - start + 1))
}

internal fun historyBucketFor(durationMs: Long): Long = maxOf(
    MINUTE_MS,
    ceil(durationMs.coerceAtLeast(1).toDouble() / MAX_CHART_POINTS).toLong(),
)

internal fun historyBucketAllowed(gaps: Int, connections: Int, charging: Int, discharging: Int, direction: HistoryDirectionFilter): Boolean {
    if (gaps > 0 || connections > 1 || (charging > 0 && discharging > 0)) return false
    return when (direction) {
        HistoryDirectionFilter.ALL -> true
        HistoryDirectionFilter.CHARGING -> charging > 0
        HistoryDirectionFilter.DISCHARGING -> discharging > 0
    }
}

internal data class HistoryUiState(
    val range: HistoryRange? = HistoryRange.WEEK,
    val startEpochMs: Long = 0,
    val endEpochMs: Long = 0,
    val bucketMs: Long = MINUTE_MS,
    val points: List<TelemetryChartPoint> = emptyList(),
    val recordings: List<RecordingHistoryItem> = emptyList(),
    val daily: List<DailySummaryEntity> = emptyList(),
    val solarDays: List<SolarDailyRollupEntity> = emptyList(),
    val flowRecordingCount: Int = 0,
    val capacityTests: List<CapacityTestSummary> = emptyList(),
    val importingCapacityTest: Boolean = false,
    val importMessage: String? = null,
    val deleting: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class HistoryViewModel(private val repository: GentaiTelemetryRepository, private val capacityRepository: CapacityTestRepository, private val energyPreferences: EnergyPreferences) : ViewModel() {
    private val initialSelection = HistorySelection(HistoryRange.WEEK, HistoryRange.WEEK.window(System.currentTimeMillis(), energyPreferences.settings.value.zone))
    private val selection = MutableStateFlow(initialSelection)
    private val visibleDayCount = MutableStateFlow(14)
    private val deleting = MutableStateFlow(false)
    private val importing = MutableStateFlow(false)
    private val importMessage = MutableStateFlow<String?>(null)

    private val historyState = selection.flatMapLatest { selected ->
        val window = selected.window
        val zone = energyPreferences.settings.value.zone
        val lastDate = Instant.ofEpochMilli(window.endEpochMs).atZone(zone).toLocalDate()
        val firstDate = Instant.ofEpochMilli(window.startEpochMs).atZone(zone).toLocalDate()
        combine(
            repository.chartPoints(window.startEpochMs, window.endEpochMs, window.bucketMs),
            repository.recordingsBetween(window.startEpochMs, window.endEpochMs),
            repository.dailySummariesBetween(window.startEpochMs, window.endEpochMs),
            repository.solarDaysBetween(firstDate.toString(), lastDate.toString()),
            repository.flowRecordingCount(window.startEpochMs, window.endEpochMs),
            deleting,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            HistoryUiState(range = selected.range, startEpochMs = window.startEpochMs, endEpochMs = window.endEpochMs, bucketMs = window.bucketMs,
                points = values[0] as List<TelemetryChartPoint>, recordings = values[1] as List<RecordingHistoryItem>,
                daily = values[2] as List<DailySummaryEntity>, solarDays = values[3] as List<SolarDailyRollupEntity>,
                flowRecordingCount = values[4] as Int, deleting = values[5] as Boolean)
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        historyState,
        capacityRepository.observeSummaries(),
        importing,
        importMessage,
    ) { history, capacityTests, isImporting, message ->
        history.copy(capacityTests = capacityTests, importingCapacityTest = isImporting, importMessage = message)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HistoryUiState(
            range = initialSelection.range,
            startEpochMs = initialSelection.window.startEpochMs,
            endEpochMs = initialSelection.window.endEpochMs,
            bucketMs = initialSelection.window.bucketMs,
        ),
    )

    fun selectRange(range: HistoryRange) {
        visibleDayCount.value = 14
        selection.value = HistorySelection(range, range.window(System.currentTimeMillis(), energyPreferences.settings.value.zone))
    }

    fun selectDates(pickerStartEpochMs: Long, pickerEndEpochMs: Long) {
        visibleDayCount.value = 14
        selection.value = HistorySelection(null, selectedHistoryDatesWindow(pickerStartEpochMs, pickerEndEpochMs, energyPreferences.settings.value.zone))
    }

    fun showOlderDays() { visibleDayCount.value += 14 }
    val loadedDayCount: StateFlow<Int> = visibleDayCount

    fun selectLocalDay(dayStartEpochMs: Long) {
        val day = Instant.ofEpochMilli(dayStartEpochMs).atZone(energyPreferences.settings.value.zone).toLocalDate()
        val zone = energyPreferences.settings.value.zone
        visibleDayCount.value = 14
        selection.value = HistorySelection(null, HistoryWindow(day.atStartOfDay(zone).toInstant().toEpochMilli(), day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1, 5 * MINUTE_MS))
    }

    fun deleteTelemetryHistory() = viewModelScope.launch {
        if (deleting.value) return@launch
        deleting.value = true
        try {
            repository.deleteTelemetryHistory(energyPreferences)
        } finally {
            deleting.value = false
        }
    }

    fun importCapacityTest(openReader: () -> Reader?) = viewModelScope.launch(Dispatchers.IO) {
        if (importing.value) return@launch
        importing.value = true
        importMessage.value = null
        try {
            val result = openReader()?.use { capacityRepository.importCsv(it) } ?: error("Could not open the selected file")
            importMessage.value = "Imported ${result.sampleCount} capacity-test samples"
        } catch (error: Exception) {
            importMessage.value = error.message ?: "Capacity-test import failed"
        } finally {
            importing.value = false
        }
    }

    fun recordingChart(recording: FlowRecordingEntity): Flow<List<RecordingChartPoint>> {
        val end = recording.endedAtMs ?: System.currentTimeMillis()
        return repository.recordingChart(recording.id, recording.startedAtMs, end, historyBucketFor(end - recording.startedAtMs + 1))
    }
    fun recordingById(id: Long): Flow<FlowRecordingEntity?> = repository.flowRecordingById(id)
    fun dayRecordCount(startMs: Long, endMs: Long, filter: HistoryDirectionFilter): Flow<Int> =
        repository.dayFlowRecordingCount(startMs, endMs, filter.mode)
    fun dayRecords(startMs: Long, endMs: Long, filter: HistoryDirectionFilter, limit: Int): Flow<List<FlowRecordingEntity>> =
        repository.dayFlowRecordings(startMs, endMs, filter.mode, limit)
}

private enum class HistoryMetric(val label: String, val unit: String) {
    POWER("Power", "W"),
    VOLTAGE("Voltage", "V"),
    CURRENT("Current", "A"),
    SOC("SOC", "%"),
    CELL_DELTA("Cell delta", "mV"),
    MOS_TEMP("MOS temp", "°C"),
    AMBIENT_TEMP("Ambient", "°C"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val historyViewModel: HistoryViewModel = viewModel {
        val database = TelemetryDatabase.get(context)
        HistoryViewModel(GentaiTelemetryRepository(database), CapacityTestRepository(database), EnergyPreferences.get(context))
    }
    val state by historyViewModel.uiState.collectAsStateWithLifecycle()
    val energySettings by EnergyPreferences.get(context).settings.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var selectDates by remember { mutableStateOf(false) }
    var selectedMetric by rememberSaveable { mutableStateOf(HistoryMetric.VOLTAGE) }
    var selectedFlowRecordingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var directionFilter by rememberSaveable { mutableStateOf(HistoryDirectionFilter.ALL) }
    var expandedDays by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var moreHistory by rememberSaveable { mutableStateOf(false) }
    val loadedDayCount by historyViewModel.loadedDayCount.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var savedListIndex by rememberSaveable { mutableStateOf(0) }
    var savedListOffset by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = selectedFlowRecordingId != null) { selectedFlowRecordingId = null }
    LaunchedEffect(selectedFlowRecordingId) {
        if (selectedFlowRecordingId != null) listState.scrollToItem(0)
        else listState.scrollToItem(savedListIndex, savedListOffset)
    }
    LaunchedEffect(state.startEpochMs, state.endEpochMs) {
        if (selectedFlowRecordingId == null) listState.scrollToItem(0)
    }
    val zone = energySettings.zone
    val firstDate = remember(state.startEpochMs, zone) { Instant.ofEpochMilli(state.startEpochMs).atZone(zone).toLocalDate() }
    val lastDate = remember(state.endEpochMs, zone) { Instant.ofEpochMilli(state.endEpochMs).atZone(zone).toLocalDate() }
    val allDayCount = (java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate) + 1).toInt().coerceAtLeast(0)
    val dayStarts = remember(lastDate, allDayCount, loadedDayCount, zone) {
        (0 until minOf(allDayCount, loadedDayCount)).map { offset -> lastDate.minusDays(offset.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
    }
    val selectedFlowRecording by produceState<FlowRecordingEntity?>(initialValue = null, selectedFlowRecordingId) {
        selectedFlowRecordingId?.let { historyViewModel.recordingById(it).collect { recording -> value = recording } }
    }
    val selectedFlowChart by produceState<List<RecordingChartPoint>>(initialValue = emptyList(), selectedFlowRecording?.id, selectedFlowRecording?.sampleCount) {
        selectedFlowRecording?.let { historyViewModel.recordingChart(it).collect { points -> value = points } }
    }
    val capacityPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { historyViewModel.importCapacityTest { context.contentResolver.openInputStream(it)?.reader() } }
    }

    HistoryStatusBarLightMode()
    MaterialTheme(colorScheme = HistoryDarkColors) {
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete telemetry history?") },
            text = { Text("This permanently removes recorded telemetry, power sessions and daily summaries. Capacity tests are kept. If monitoring is active, new samples will continue to be recorded.") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    historyViewModel.deleteTelemetryHistory()
                }) { Text("Delete history") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (selectDates) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { selectDates = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis ?: return@TextButton
                        historyViewModel.selectDates(start, pickerState.selectedEndDateMillis ?: start)
                        selectDates = false
                    },
                ) { Text("Show dates") }
            },
            dismissButton = { TextButton(onClick = { selectDates = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = pickerState, title = { Text("Select history dates", Modifier.padding(16.dp)) })
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(if (selectedFlowRecording == null) "History" else "Recording detail", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { if (selectedFlowRecordingId != null) selectedFlowRecordingId = null else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val focusedRecording = selectedFlowRecording
            if (focusedRecording != null) {
                item { FlowRecordingCard(focusedRecording, energySettings, selected = true, onClick = { selectedFlowRecordingId = null }, zone = zone) }
                item { FlowRecordingDetailCard(focusedRecording, selectedFlowChart, zone) }
            } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Date range", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistoryRange.entries.forEach { range ->
                            FilterChip(selected = state.range == range, onClick = { expandedDays = emptyList(); historyViewModel.selectRange(range) }, label = { Text(range.label) })
                        }
                    }
                    TextButton(onClick = { selectDates = true }) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.range == null) "Change selected dates" else "Choose dates")
                    }
                    Text(
                        "${formatHistoryDateOnly(state.startEpochMs, zone)} – ${formatHistoryDateOnly(state.endEpochMs, zone)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { HistoryTotalsCard(state, energySettings) }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Charging and discharging", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Signed battery power over this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(HistoryDirectionFilter.entries, key = { it.name }) { option ->
                                FilterChip(selected = directionFilter == option, onClick = { directionFilter = option }, label = { Text(option.label) })
                            }
                        }
                    }
                }
            }
            item { HistoryGraphCard(state.points, HistoryMetric.POWER, state.startEpochMs, state.endEpochMs, state.bucketMs, directionFilter, zone) }

            item { Text("By day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(dayStarts, key = { "day-$it" }) { dayStart ->
                val daySummary = state.solarDays.firstOrNull { it.localDate == Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate().toString() }
                val expanded = dayStart in expandedDays
                HistoryDayCard(dayStart, daySummary, expanded, zone, energySettings.trackingStartMs, state.endEpochMs, directionFilter, historyViewModel,
                    onToggle = { expandedDays = if (expanded) expandedDays - dayStart else expandedDays + dayStart },
                    onViewGraph = { historyViewModel.selectLocalDay(dayStart) },
                    onOpen = { savedListIndex = listState.firstVisibleItemIndex; savedListOffset = listState.firstVisibleItemScrollOffset; selectedFlowRecordingId = it.id })
            }
            if (dayStarts.size < allDayCount) item { TextButton(onClick = { historyViewModel.showOlderDays() }) { Text("Show older days") } }

            item { Card(onClick = { moreHistory = !moreHistory }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Text(if (moreHistory) "More history  ▴" else "More history  ▾", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) } }
            if (moreHistory) {

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Legacy recordings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Older mixed recordings are retained and classified into Charging or Discharging in Analytics.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.recordings.isEmpty()) {
                item { Text("No legacy recordings in this date range.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.recordings, key = { "recording-${it.id}" }) { recording ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        RecordingRow(recording)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Capacity tests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { capacityPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) }, enabled = !state.importingCapacityTest) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import CSV")
                    }
                    state.importMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    if (state.capacityTests.isEmpty()) {
                        Text("No capacity tests yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.capacityTests, key = { "capacity-${it.test.id}" }) { summary ->
                CapacityTestCard(summary)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Icon(Icons.Rounded.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Telemetry graph", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(HistoryMetric.entries.filter { it != HistoryMetric.POWER }, key = { it.name }) { metric ->
                            FilterChip(selected = selectedMetric == metric, onClick = { selectedMetric = metric }, label = { Text(metric.label) })
                        }
                    }
                }
            }
            item(key = "graph-${selectedMetric.name}") { HistoryGraphCard(state.points, selectedMetric, state.startEpochMs, state.endEpochMs, state.bucketMs, HistoryDirectionFilter.ALL, zone) }

            item { Text("Legacy UTC daily summaries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (state.daily.isEmpty()) {
                item { Text("No daily summaries in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.daily, key = { it.dayStartEpochMs }) { day ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DailyRow(day)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HistoryTotal("Value", energySettings.potentialSolarValueFor(day.chargedWh)?.let { "₱${formatHistoryNumber(it)}" } ?: "₱—", Modifier.weight(1f))
                                HistoryTotal("Saved", energySettings.savingsFor(day.dischargedWh)?.let { "₱${formatHistoryNumber(it)}" } ?: "₱—", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Data management", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Remove telemetry and power sessions. Capacity tests are kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { confirmDelete = true }, enabled = !state.deleting) {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.deleting) "Deleting…" else "Delete telemetry history")
                        }
                    }
                }
            }
            }
            }
        }
    }
    }
}

@Composable
private fun FlowRecordingCard(recording: FlowRecordingEntity, settings: EnergySettings, selected: Boolean, onClick: () -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    val charging = recording.mode == "CHARGING"
    val color = if (charging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val energyWh = if (charging) recording.chargedWh else recording.dischargedWh
    val money = if (charging) settings.potentialSolarValueFor(energyWh) else settings.savingsFor(energyWh)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(12.dp)) {
                    Icon(if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.padding(8.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (charging) "Charging recording" else "Discharging recording", color = color, fontWeight = FontWeight.Bold)
                    Text(formatHistoryDate(recording.startedAtMs, zone) + (recording.endedAtMs?.let { " – ${formatHistoryDate(it, zone)}" } ?: " • active"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(formatHistoryEnergy(energyWh), fontWeight = FontWeight.Bold, color = color)
                    Text(money?.let { "₱${formatHistoryNumber(it)}" } ?: "₱—", style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryTotal("Duration", formatHistoryDuration((recording.endedAtMs ?: System.currentTimeMillis()) - recording.startedAtMs), Modifier.weight(1f))
                HistoryTotal("Avg", "${formatHistoryNumber(recording.averagePowerW)} W", Modifier.weight(1f))
                HistoryTotal("Peak", "${formatHistoryNumber(recording.peakPowerW)} W", Modifier.weight(1f))
                HistoryTotal("Coverage", formatHistoryNumber(recording.coveragePct) + "%", Modifier.weight(1f))
            }
            Text("Watts, Amps and SOC below", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryDayCard(
    dayStartMs: Long, summary: SolarDailyRollupEntity?, expanded: Boolean,
    zone: ZoneId, trackingStartMs: Long, periodEndMs: Long, direction: HistoryDirectionFilter,
    viewModel: HistoryViewModel, onToggle: () -> Unit, onViewGraph: () -> Unit,
    onOpen: (FlowRecordingEntity) -> Unit,
) {
    var visibleRecordCount by rememberSaveable(dayStartMs) { mutableStateOf(50) }
    val nextDayStart = Instant.ofEpochMilli(dayStartMs).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEndMs = minOf(nextDayStart - 1, periodEndMs)
    val countFlow = remember(dayStartMs, dayEndMs, direction) { viewModel.dayRecordCount(dayStartMs, dayEndMs, direction) }
    val recordCount by countFlow.collectAsStateWithLifecycle(initialValue = 0)
    val recordings by produceState<List<FlowRecordingEntity>>(initialValue = emptyList(), expanded, dayStartMs, dayEndMs, direction, visibleRecordCount) {
        if (expanded) viewModel.dayRecords(dayStartMs, dayEndMs, direction, visibleRecordCount).collect { rows -> value = rows }
    }
    val expectedMs = (minOf(nextDayStart, periodEndMs + 1, System.currentTimeMillis()) - maxOf(dayStartMs, trackingStartMs)).coerceAtLeast(0)
    val coverageText = if (summary != null && expectedMs > 0) "${formatHistoryNumber((summary.observedMs * 100.0 / expectedMs).coerceIn(0.0, 100.0))}% coverage" else "No coverage data"
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onToggle, color = androidx.compose.ui.graphics.Color.Transparent) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatHistoryDateOnly(dayStartMs, zone), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (expanded) "▴" else "▾", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(summary?.let { "Charged ${formatHistoryNumber(it.chargedWh / 1000.0)} kWh  ·  Used ${formatHistoryNumber(it.usedWh / 1000.0)} kWh" } ?: "No energy summary", style = MaterialTheme.typography.bodyMedium)
                    Text("$recordCount records  ·  $coverageText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expanded) {
                TextButton(onClick = onViewGraph) { Text("View day graph") }
                if (recordCount == 0) Text("No charging or discharging records for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                recordings.take(visibleRecordCount).forEach { recording ->
                    val continued = recording.startedAtMs < dayStartMs || (recording.endedAtMs ?: System.currentTimeMillis()) >= nextDayStart
                    Card(onClick = { onOpen(recording) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("${if (recording.mode == "CHARGING") "Charging" else "Discharging"}  ·  ${formatHistoryEnergy(if (recording.mode == "CHARGING") recording.chargedWh else recording.dischargedWh)}${if (continued) " total" else ""}", fontWeight = FontWeight.SemiBold)
                            Text("${formatHistoryDate(recording.startedAtMs, zone)}  ·  ${recording.startSocPct ?: "–"}% → ${recording.endSocPct ?: "–"}%${if (continued) "  ·  Continues across days" else if (recording.endedAtMs == null) "  ·  Active" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (recordCount > visibleRecordCount) TextButton(onClick = { visibleRecordCount += 50 }) { Text("Show more records") }
            }
        }
    }
}

@Composable
private fun HistoryFlowOverviewCard(recordings: List<FlowRecordingEntity>, settings: EnergySettings) {
    val chargedWh = recordings.filter { it.mode == "CHARGING" }.sumOf { it.chargedWh }
    val usedWh = recordings.filter { it.mode == "DISCHARGING" }.sumOf { it.dischargedWh }
    val hasData = recordings.isNotEmpty()
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Flow summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Measured in this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HistoryInfoTooltip("Value uses your ₱/kWh setting. Saved is estimated from measured discharge.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HistoryValueTile("Charged", if (hasData) "${formatHistoryNumber(chargedWh / 1000.0)} kWh" else "N/A", if (hasData) settings.potentialSolarValueFor(chargedWh)?.let { "₱${formatHistoryNumber(it)} value" } ?: "₱—" else "₱—", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                HistoryValueTile("Used", if (hasData) "${formatHistoryNumber(usedWh / 1000.0)} kWh" else "N/A", if (hasData) settings.savingsFor(usedWh)?.let { "₱${formatHistoryNumber(it)} saved" } ?: "₱—" else "₱—", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryValueTile(label: String, value: String, money: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.background(accent.copy(alpha = 0.10f), RoundedCornerShape(16.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
        Text(money, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryInfoTooltip(message: String) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(message) } },
        state = androidx.compose.material3.rememberTooltipState(),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = "More information", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class FlowChartMetric(val label: String, val unit: String) { WATTS("Watts", "W"), AMPS("Amps", "A"), SOC("SOC", "%") }

@Composable
private fun FlowRecordingDetailCard(recording: FlowRecordingEntity, samples: List<RecordingChartPoint>, zone: ZoneId) {
    var metric by remember(recording.id) { mutableStateOf(FlowChartMetric.WATTS) }
    var selectedIndex by remember(recording.id) { mutableStateOf<Int?>(null) }
    var selectedTimeMs by remember(recording.id) { mutableStateOf<Long?>(null) }
    val display = remember(samples, metric) {
        val bucketMs = historyBucketFor((recording.endedAtMs ?: System.currentTimeMillis()) - recording.startedAtMs + 1)
        var previousTime: Long? = null
        var breakPending = false
        val readings = samples.mapNotNull { sample ->
            if (sample.gapCount > 0) {
                breakPending = true
                return@mapNotNull null
            }
            val gap = breakPending || previousTime?.let { sample.timestampEpochMs - it > bucketMs * 2 } == true
            previousTime = sample.timestampEpochMs
            breakPending = false
                TrendReading(
                    timestampMs = sample.timestampEpochMs,
                    value = when (metric) {
                        FlowChartMetric.WATTS -> sample.powerW
                        FlowChartMetric.AMPS -> sample.currentA
                        FlowChartMetric.SOC -> sample.socPct
                    },
                    startMs = sample.timestampEpochMs, endMs = sample.endEpochMs,
                    minimum = when (metric) { FlowChartMetric.WATTS -> sample.minPowerW; FlowChartMetric.AMPS -> sample.minCurrentA; FlowChartMetric.SOC -> sample.minSocPct },
                    maximum = when (metric) { FlowChartMetric.WATTS -> sample.maxPowerW; FlowChartMetric.AMPS -> sample.maxCurrentA; FlowChartMetric.SOC -> sample.maxSocPct },
                    sampleCount = sample.sampleCount,
                    gapBefore = gap,
                    exact = false,
                )
            }
        if (readings.isEmpty()) emptyList() else ReadableTrend.reduce(readings, samples.first().timestampEpochMs, samples.last().timestampEpochMs, maxContinuousGapMs = null, targetBuckets = 720, minimumBucketMs = bucketMs)
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${if (recording.mode == "CHARGING") "Charging" else "Discharging"} graph", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(FlowChartMetric.entries, key = { it.name }) { option -> FilterChip(selected = metric == option, onClick = { metric = option; selectedIndex = null; selectedTimeMs = null }, label = { Text(option.label) }) } }
            if (display.size >= 2) {
                ReadableTrendChart(
                    buckets = display,
                    startMs = samples.first().timestampEpochMs,
                    endMs = samples.last().timestampEpochMs,
                    unit = metric.unit,
                    color = if (recording.mode == "CHARGING") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    scale = if (metric == FlowChartMetric.SOC) TrendScale.SOC else TrendScale.SIGNED_ZERO,
                    zoneId = zone,
                    selectedIndex = selectedIndex,
                    onSelect = { index, time -> selectedIndex = index; selectedTimeMs = time },
                    selectedTimeMs = selectedTimeMs,
                )
            } else Text("More samples are needed for this recording.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${formatHistoryEnergy(if (recording.mode == "CHARGING") recording.chargedWh else recording.dischargedWh)} • ${recording.sampleCount} samples • ${formatHistoryNumber(recording.coveragePct)}% coverage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
private fun HistoryGraphCard(points: List<TelemetryChartPoint>, metric: HistoryMetric, startMs: Long, endMs: Long, sourceBucketMs: Long, direction: HistoryDirectionFilter, zone: ZoneId) {
    var selectedIndex by remember(metric, startMs, endMs) { mutableStateOf<Int?>(null) }
    var selectedTimeMs by remember(metric, startMs, endMs) { mutableStateOf<Long?>(null) }
    val display = remember(points, metric, startMs, endMs, sourceBucketMs, direction) {
        val readings = mutableListOf<TrendReading>()
        var previousBucketIndex: Long? = null
        var breakPending = false
        var previousSign = 0
        points.forEach { point ->
            // A SQL bucket with an internal gap would mix readings from both sides.
            // Omit that display bucket, then start a new line at the next valid one.
            if (!historyBucketAllowed(point.gapCount, point.connectionCount, point.chargingCount, point.dischargingCount, direction)) {
                breakPending = true
                return@forEach
            }
            val value = point.valueFor(metric)
            val range = point.rangeFor(metric)
            if (value == null || range == null) {
                breakPending = true
                return@forEach
            }
            val (low, high) = range
            val bucketIndex = Math.floorDiv(point.timestampEpochMs - startMs, sourceBucketMs)
            val skippedBucket = previousBucketIndex?.let { bucketIndex - it > 1 } ?: false
            val sign = value.compareTo(0.0)
            readings += TrendReading(
                timestampMs = point.timestampEpochMs,
                value = value,
                startMs = point.timestampEpochMs,
                endMs = point.endEpochMs,
                minimum = low,
                maximum = high,
                sampleCount = point.sampleCount,
                gapBefore = breakPending || skippedBucket || (previousSign != 0 && sign != 0 && sign != previousSign),
                exact = false,
            )
            previousBucketIndex = bucketIndex
            previousSign = sign
            breakPending = false
        }
        ReadableTrend.reduce(readings, startMs, endMs, maxContinuousGapMs = null, targetBuckets = 720, minimumBucketMs = sourceBucketMs)
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(metric.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (display.size >= 2) {
                ReadableTrendChart(
                    buckets = display,
                    startMs = startMs,
                    endMs = endMs,
                    unit = metric.unit,
                    color = MaterialTheme.colorScheme.primary,
                    scale = when (metric) {
                        HistoryMetric.SOC -> TrendScale.SOC
                        HistoryMetric.POWER, HistoryMetric.CURRENT, HistoryMetric.CELL_DELTA -> TrendScale.SIGNED_ZERO
                        else -> TrendScale.PADDED
                    },
                    zoneId = zone,
                    negativeColor = if (metric == HistoryMetric.POWER || metric == HistoryMetric.CURRENT) MaterialTheme.colorScheme.tertiary else null,
                    selectedIndex = selectedIndex,
                    onSelect = { index, time -> selectedIndex = index; selectedTimeMs = time },
                )
                if (metric == HistoryMetric.POWER) Text("Green: charging  ·  Amber: discharging  ·  Breaks: missing or mixed data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (metric == HistoryMetric.POWER) selectedTimeMs?.let { selected ->
                    val point = points.filter { historyBucketAllowed(it.gapCount, it.connectionCount, it.chargingCount, it.dischargingCount, direction) }
                        .minByOrNull { kotlin.math.abs(it.timestampEpochMs - selected) }
                    point?.let { Text("${formatHistoryDate(it.timestampEpochMs, zone)}  ·  ${formatHistoryNumber(it.powerW)} W  ·  ${formatHistoryNumber(it.currentA)} A  ·  ${formatHistoryNumber(it.socPct)}% SOC", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(
                    "${display.sumOf { it.sampleCount }} readings • ${display.count { it.gapBefore }} breaks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Not enough data in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecordingRow(recording: RecordingHistoryItem) {
    val end = recording.endedAtEpochMs ?: System.currentTimeMillis()
    val title = when {
        recording.endedAtEpochMs == null -> "Recording now"
        recording.endReason == "Imported capacity test" -> "Imported recording"
        else -> "Saved recording"
    }
    val soc = if (recording.startSocPct != null && recording.endSocPct != null) {
        "${recording.startSocPct}% → ${recording.endSocPct}%"
    } else {
        "SOC unavailable"
    }
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    "${formatHistoryDate(recording.startedAtEpochMs)} • ${formatHistoryDuration(end - recording.startedAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(soc, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryTotal("Samples", recording.sampleCount.toString(), Modifier.weight(1f))
            HistoryTotal("Sample mean", recording.averageAbsolutePowerW?.let { "${formatHistoryNumber(it)} W" } ?: "—", Modifier.weight(1f))
            HistoryTotal("Peak", recording.peakAbsolutePowerW?.let { "${formatHistoryNumber(it)} W" } ?: "—", Modifier.weight(1f))
        }
        Text(
            "Minimum ${recording.minimumVoltageV?.let { "${formatHistoryNumber(it)} V" } ?: "—"} • MOS/ambient max ${recording.maximumTemperatureC?.let { "$it °C" } ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CapacityTestCard(summary: CapacityTestSummary) {
    val result = summary.result
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Capacity test", fontWeight = FontWeight.Bold)
                    Text(formatHistoryDate(summary.test.startedAtMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${result.startSocPct ?: "—"}% → ${result.endSocPct ?: "—"}%", fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryTotal("Energy", "${formatHistoryNumber(maxOf(result.dischargedWh, result.chargedWh))} Wh", Modifier.weight(1f))
                HistoryTotal("Capacity", "${formatHistoryNumber(maxOf(result.dischargedAh, result.chargedAh))} Ah", Modifier.weight(1f))
                HistoryTotal("Average", "${formatHistoryNumber(result.averagePowerW)} W", Modifier.weight(1f))
            }
            Text(
                "${formatHistoryDuration(result.observedMs + result.missingMs)} • peak ${formatHistoryNumber(result.peakPowerW)} W • min ${result.minVoltageV?.let(::formatHistoryNumber) ?: "—"} V • ${summary.sampleCount} samples • ${formatHistoryNumber(result.coveragePct)}% coverage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryTotalsCard(state: HistoryUiState, settings: EnergySettings) {
    val dischargedWh = state.solarDays.sumOf { it.usedWh }
    val chargedWh = state.solarDays.sumOf { it.chargedWh }
    val observedMs = state.solarDays.sumOf { it.observedMs }
    val totalMs = (minOf(state.endEpochMs + 1, System.currentTimeMillis()) - maxOf(state.startEpochMs, settings.trackingStartMs)).coerceAtLeast(0)
    val coverage = if (totalMs > 0) (observedMs * 100.0 / totalMs).coerceIn(0.0, 100.0) else 0.0
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Period totals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Energy and estimated savings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HistoryInfoTooltip("Energy is measured at the battery. Savings use the configured ₱/kWh tariff.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HistoryValueTile("Charged", if (state.solarDays.isEmpty()) "N/A" else "${formatHistoryNumber(chargedWh / 1000.0)} kWh", settings.potentialSolarValueFor(chargedWh)?.takeIf { state.solarDays.isNotEmpty() }?.let { "₱${formatHistoryNumber(it)} value" } ?: "₱—", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                HistoryValueTile("Used", if (state.solarDays.isEmpty()) "N/A" else "${formatHistoryNumber(dischargedWh / 1000.0)} kWh", settings.savingsFor(dischargedWh)?.takeIf { state.solarDays.isNotEmpty() }?.let { "₱${formatHistoryNumber(it)} saved" } ?: "₱—", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryTotal("Coverage", if (totalMs > 0) "${formatHistoryNumber(coverage)}%" else "N/A", Modifier.weight(1f))
                HistoryTotal("Days", state.solarDays.size.toString(), Modifier.weight(1f))
                HistoryTotal("Records", state.flowRecordingCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryTotal(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

private fun TelemetryChartPoint.valueFor(metric: HistoryMetric): Double? = when (metric) {
    HistoryMetric.POWER -> powerW
    HistoryMetric.VOLTAGE -> voltageV
    HistoryMetric.CURRENT -> currentA
    HistoryMetric.SOC -> socPct
    HistoryMetric.CELL_DELTA -> cellDeltaV?.times(1000)
    HistoryMetric.MOS_TEMP -> mosTemperatureC
    HistoryMetric.AMBIENT_TEMP -> ambientTemperatureC
}

private fun TelemetryChartPoint.rangeFor(metric: HistoryMetric): Pair<Double, Double>? = when (metric) {
    HistoryMetric.POWER -> minPowerW to maxPowerW
    HistoryMetric.VOLTAGE -> minVoltageV to maxVoltageV
    HistoryMetric.CURRENT -> minCurrentA to maxCurrentA
    HistoryMetric.SOC -> minSocPct to maxSocPct
    HistoryMetric.CELL_DELTA -> if (minCellDeltaV != null && maxCellDeltaV != null) minCellDeltaV * 1000 to maxCellDeltaV * 1000 else null
    HistoryMetric.MOS_TEMP -> minMosTemperatureC to maxMosTemperatureC
    HistoryMetric.AMBIENT_TEMP -> minAmbientTemperatureC to maxAmbientTemperatureC
}

@Composable
private fun HistoryStatusBarLightMode() {
    val context = LocalContext.current
    val view = LocalView.current
    androidx.compose.runtime.DisposableEffect(view, context) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}

private fun formatHistoryNumber(value: Double): String = "%.1f".format(value)
private fun formatHistoryEnergy(energyWh: Double): String =
    if (energyWh < 100.0) "${formatHistoryNumber(energyWh)} Wh" else "${formatHistoryNumber(energyWh / 1000.0)} kWh"

private val historyDateFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
private fun formatHistoryDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(epochMs)
    .atZone(zone)
    .format(historyDateFormatter)

private val historyDateOnlyFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private fun formatHistoryDateOnly(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(epochMs)
    .atZone(zone)
    .format(historyDateOnlyFormatter)

private fun formatHistoryDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    if (totalSeconds < 60) return "${totalSeconds}s"
    val totalMinutes = totalSeconds / 60
    return if (totalMinutes < 60) "${totalMinutes}m" else "${totalMinutes / 60}h ${totalMinutes % 60}m"
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS
private const val MAX_CHART_POINTS = 720.0
