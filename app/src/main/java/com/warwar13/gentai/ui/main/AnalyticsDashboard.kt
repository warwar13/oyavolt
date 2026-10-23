package com.warwar13.gentai.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.warwar13.gentai.analytics.*
import com.warwar13.gentai.data.GentaiTelemetryRepository
import com.warwar13.gentai.data.SolarEnergyRepository
import com.warwar13.gentai.data.TelemetryDatabase
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import kotlin.math.abs

internal val AnalyticsLime = Color(0xFFA8F05A)
internal val AnalyticsAmber = Color(0xFFFFC857)
private val Panel = Color(0xFF101A14)
private val AnalyticsColors = darkColorScheme(
    primary = AnalyticsLime, onPrimary = Color(0xFF142000), primaryContainer = Color(0xFF203A18),
    tertiary = AnalyticsAmber, background = Color(0xFF070A08), surface = Panel,
    onSurface = Color(0xFFF1F7ED), onBackground = Color(0xFFF1F7ED),
    surfaceVariant = Color(0xFF202C23), onSurfaceVariant = Color(0xFFADB8A9), outline = Color(0xFF667063),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier, onSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val database = remember(context) { TelemetryDatabase.get(context) }
    val solar = remember(database) { SolarEnergyRepository(database) }
    val vm: AnalyticsViewModel = viewModel { AnalyticsViewModel(GentaiTelemetryRepository(database), solar) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by EnergyPreferences.get(context).settings.collectAsStateWithLifecycle()
    val days by remember(solar) { solar.observeDays() }.collectAsStateWithLifecycle(initialValue = null)
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000) }
    }
    val today = Instant.ofEpochMilli(now).atZone(settings.zone).toLocalDate()
    var period by rememberSaveable { mutableStateOf(SolarPeriod.DAY) }
    var dateText by rememberSaveable(settings.reportingZoneId) { mutableStateOf(today.toString()) }
    val date = LocalDate.parse(dateText)
    LaunchedEffect(date, settings, period) {
        // No day-specific queries are needed for aggregate views.
        if (period == SolarPeriod.DAY) vm.select(date, settings) else vm.stopDayQueries()
    }
    val matchingDay = state.date == date && state.settings == settings
    val loading = if (period == SolarPeriod.DAY) !matchingDay || (state.snapshot == null && state.error == null) else days == null
    val error = if (period == SolarPeriod.DAY && matchingDay) state.error else null
    val report = remember(days, state.snapshot, state.nowMs, matchingDay, period, date, settings, now) {
        val rows = if (period == SolarPeriod.DAY) listOfNotNull(state.snapshot?.daily.takeIf { matchingDay }) else days.orEmpty()
        SolarReports.make(rows, settings, period, date, if (period == SolarPeriod.DAY && matchingDay) state.nowMs else now, if (matchingDay) state.snapshot?.hourly.orEmpty() else emptyList())
    }
    val allTime = remember(days, settings, now) { SolarReports.make(days.orEmpty(), settings, SolarPeriod.ALL_TIME, today, now) }
    val listState = rememberLazyListState()
    LaunchedEffect(period, dateText) { listState.scrollToItem(0) }
    MaterialTheme(colorScheme = AnalyticsColors) {
        Scaffold(
            modifier = modifier.fillMaxSize(), containerColor = AnalyticsColors.background,
            topBar = { TopAppBar(title = { Text("Analytics", fontWeight = FontWeight.Bold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = AnalyticsColors.background)) },
        ) { padding ->
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(), state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("period") {
                    ChoiceRow(SolarPeriod.entries, period, { if (it == SolarPeriod.ALL_TIME) "Total" else it.name.lowercase().replaceFirstChar(Char::uppercase) }) {
                        period = it; dateText = today.toString()
                    }
                }
                item("date") { PeriodNavigator(period, date, today, settings.zone, { dateText = shiftAnalyticsDate(date, period, it).toString() }, { dateText = today.toString() }) }
                if (loading) {
                    item { Text("Loading recorded energy…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else if (error != null) {
                    item { Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                } else {
                    item("summary") { EnergySummary(report, if (period == SolarPeriod.DAY) state.samples else emptyList(), settings.zone) }
                    item("chart") { AnalyticsChartCard(report, if (period == SolarPeriod.DAY) state.samples else emptyList(), settings.zone, state.nowMs) }
                    item("durations") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmallStat("Charging time", analyticsDuration(report.amount.chargingMs), AnalyticsLime, Modifier.weight(1f))
                            SmallStat("Discharging time", analyticsDuration(report.amount.dischargingMs), AnalyticsAmber, Modifier.weight(1f))
                        }
                    }
                    if (period == SolarPeriod.DAY) {
                        item("details") { DayBatteryDetails(state, settings.zone) }
                        item("periods") { RecordedPeriods(state.directional, settings.zone) }
                    }
                    item("value") { AnalyticsValueDetails(report, allTime, settings, onSettings) }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

private fun shiftAnalyticsDate(date: LocalDate, period: SolarPeriod, step: Long): LocalDate = when (period) {
    SolarPeriod.DAY -> date.plusDays(step)
    SolarPeriod.MONTH -> date.plusMonths(step)
    SolarPeriod.YEAR -> date.plusYears(step)
    SolarPeriod.ALL_TIME -> date
}

@Composable
private fun PeriodNavigator(period: SolarPeriod, date: LocalDate, today: LocalDate, zone: ZoneId, shift: (Long) -> Unit, reset: () -> Unit) {
    val label = when (period) {
        SolarPeriod.DAY -> if (date == today) "Today · ${date.format(DateTimeFormatter.ofPattern("MMM d"))}" else date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
        SolarPeriod.MONTH -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        SolarPeriod.YEAR -> date.year.toString()
        SolarPeriod.ALL_TIME -> "Since tracking began"
    }
    val nextAllowed = when (period) {
        SolarPeriod.DAY -> date < today
        SolarPeriod.MONTH -> date.withDayOfMonth(1) < today.withDayOfMonth(1)
        SolarPeriod.YEAR -> date.year < today.year
        SolarPeriod.ALL_TIME -> false
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (period != SolarPeriod.ALL_TIME) IconButton(onClick = { shift(-1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous period") }
        Column(Modifier.weight(1f).clickable(onClickLabel = "Return to current period", onClick = reset).padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(zone.id.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (period != SolarPeriod.ALL_TIME) IconButton(onClick = { shift(1) }, enabled = nextAllowed) { Icon(Icons.Rounded.ChevronRight, "Next period") }
    }
}

@Composable
internal fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val accent = when (option) { AnalyticsFilter.DISCHARGING -> AnalyticsAmber; else -> AnalyticsLime }
            Surface(
                onClick = { onSelect(option) }, shape = RoundedCornerShape(12.dp),
                color = if (option == selected) accent.copy(alpha = 0.14f) else Color.Transparent,
                contentColor = if (option == selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 13.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(label(option), style = MaterialTheme.typography.labelLarge, fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun EnergySummary(report: SolarReport, samples: List<AnalyticsSample>, zone: ZoneId) {
    val large = LocalDensity.current.fontScale >= 1.3f
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(AnalyticsLime.copy(alpha = 0.045f), Color.Transparent))).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (large) {
                EnergyTotal("Solar charged", report.amount.chargedWh, report.hasData, AnalyticsLime)
                EnergyTotal("Battery used", report.amount.usedWh, report.hasData, AnalyticsAmber)
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EnergyTotal("Solar charged", report.amount.chargedWh, report.hasData, AnalyticsLime, Modifier.weight(1f))
                EnergyTotal("Battery used", report.amount.usedWh, report.hasData, AnalyticsAmber, Modifier.weight(1f))
            }
            val balance = report.amount.chargedWh - report.amount.usedWh
            Text("Recorded balance   ${if (!report.hasData) "—" else (if (balance > 0) "+" else "") + analyticsEnergy(balance)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            val coverage = report.coveragePct.coerceIn(0.0, 100.0)
            Text(
                if (report.expectedMs > 0) "Recorded ${analyticsDuration(report.amount.observedMs)} of ${analyticsDuration(report.expectedMs)} · ${"%.0f".format(coverage)}% coverage" else "Tracking has not started for this period",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.expectedMs > 0) {
                LinearProgressIndicator(progress = { (coverage / 100).toFloat() }, modifier = Modifier.fillMaxWidth().height(3.dp), color = if (coverage < 95) AnalyticsAmber else AnalyticsLime, trackColor = Color(0xFF29332B), gapSize = 0.dp)
                if (!report.hasData) Text("No recorded data. Energy totals are unavailable.", style = MaterialTheme.typography.bodySmall, color = AnalyticsAmber)
                else if (report.missingMs > 0) Text("Partial recording—totals cover recorded periods.", style = MaterialTheme.typography.bodySmall, color = AnalyticsAmber)
            }
            if (samples.isNotEmpty()) {
                val first = samples.first(); val last = samples.last()
                Text("SOC ${first.socPct}% → ${last.socPct}% · ${analyticsTime(first.timestampMs, zone)}–${analyticsTime(last.timestampMs, zone)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EnergyTotal(label: String, wh: Double, hasData: Boolean, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold)
        Text(if (hasData) analyticsEnergy(wh) else "—", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun AnalyticsChartCard(report: SolarReport, samples: List<AnalyticsSample>, zone: ZoneId, nowMs: Long) {
    var metric by rememberSaveable(report.period, report.anchor.toString()) { mutableStateOf(AnalyticsMetric.ENERGY) }
    var filter by rememberSaveable(report.period, report.anchor.toString()) { mutableStateOf(AnalyticsFilter.BOTH) }
    var window by rememberSaveable(report.period, report.anchor.toString()) { mutableStateOf(AnalyticsWindow.RECORDED) }
    var selectedTime by rememberSaveable(report.period, report.anchor.toString(), metric, filter, window) { mutableStateOf<Long?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val actualMetric = if (report.period == SolarPeriod.DAY) metric else AnalyticsMetric.ENERGY
    @Composable fun ChartContents(full: Boolean) {
        var showFilters by remember { mutableStateOf(false) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (actualMetric == AnalyticsMetric.ENERGY) when (report.period) { SolarPeriod.DAY -> "Energy by hour"; SolarPeriod.MONTH -> "Energy by day"; SolarPeriod.YEAR -> "Energy by month"; else -> "Energy by year" } else "${actualMetric.label} timeline",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                if (actualMetric != AnalyticsMetric.SOC) Box {
                    IconButton(onClick = { showFilters = true }) { Icon(Icons.Rounded.FilterList, "Flow filter: ${filter.label}", tint = if (filter == AnalyticsFilter.BOTH) MaterialTheme.colorScheme.onSurfaceVariant else if (filter == AnalyticsFilter.CHARGING) AnalyticsLime else AnalyticsAmber) }
                    DropdownMenu(expanded = showFilters, onDismissRequest = { showFilters = false }) {
                        AnalyticsFilter.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                leadingIcon = { RadioButton(selected = filter == option, onClick = null) },
                                onClick = { filter = option; showFilters = false },
                            )
                        }
                    }
                }
                IconButton(onClick = { expanded = !full }) { Icon(if (full) Icons.Rounded.Close else Icons.Rounded.Fullscreen, if (full) "Close expanded chart" else "Expand chart") }
            }
            if (report.period == SolarPeriod.DAY) ChoiceRow(AnalyticsMetric.entries, metric, { it.label }) { metric = it }
            if (actualMetric != AnalyticsMetric.SOC && filter != AnalyticsFilter.BOTH) Text("Showing ${filter.label.lowercase()} · Summary totals include both flows", style = MaterialTheme.typography.labelSmall, color = if (filter == AnalyticsFilter.CHARGING) AnalyticsLime else AnalyticsAmber)
            if (actualMetric != AnalyticsMetric.ENERGY) ChoiceRow(AnalyticsWindow.entries, window, { it.label }) { window = it }
            if (actualMetric == AnalyticsMetric.ENERGY) {
                AnalyticsEnergyBars(report, filter, selectedTime, { selectedTime = it }, zone, if (full) 300.dp else 185.dp)
            } else {
                val bounds = remember(samples, report.anchor, zone, nowMs, window) { analyticsWindow(samples, report.anchor.dayWindow(zone), nowMs, window) }
                val buckets = remember(samples, actualMetric, filter, bounds) { analyticsTrend(samples, actualMetric, filter, bounds) }
                val selectedIndex = selectedTime?.let { time -> buckets.indices.minByOrNull { abs(buckets[it].timestampMs - time) } }
                Text("${analyticsTime(bounds.startEpochMs, zone)}–${analyticsTime(bounds.endEpochMs, zone)} · ${zone.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (buckets.isEmpty()) {
                    Text("No recorded ${if (actualMetric == AnalyticsMetric.SOC) "battery readings" else filter.label.lowercase() + " flow"} in this window.", Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (actualMetric != AnalyticsMetric.SOC) EnergyLegend()
                    ReadableTrendChart(
                        buckets, bounds.startEpochMs, bounds.endEpochMs, actualMetric.unit, AnalyticsLime,
                        if (actualMetric == AnalyticsMetric.SOC) TrendScale.SOC else TrendScale.SIGNED_ZERO,
                        zone, selectedIndex, { index, _ -> selectedTime = index?.let { buckets[it].timestampMs } },
                        plotHeight = if (full) 320.dp else 200.dp, selectedTimeMs = selectedTime,
                        negativeColor = if (actualMetric == AnalyticsMetric.SOC) null else AnalyticsAmber, readoutBelow = true,
                    )
                    Text(if (actualMetric == AnalyticsMetric.SOC) "Breaks indicate missing readings. The percentage axis adapts to the recorded range." else "Above zero: charging · Below zero: discharging\nBreaks indicate missing readings or filtered periods.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedTime == null) Text("Tap or drag the chart to inspect a reading.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) { ChartContents(false) }
    if (expanded) Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = AnalyticsColors.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { ChartContents(true) }
        }
    }
}

@Composable
internal fun EnergyLegend() {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("● Solar charged", style = MaterialTheme.typography.labelSmall, color = AnalyticsLime)
        Text("● Battery used", style = MaterialTheme.typography.labelSmall, color = AnalyticsAmber)
    }
}

@Composable
private fun SmallStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AnalyticsDisclosure(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Collapse $title" else "Expand $title")
        }
        if (expanded) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun DayBatteryDetails(state: AnalyticsUiState, zone: ZoneId) {
    AnalyticsDisclosure("Battery details", "Selected day · Load, temperature and cells") {
        if (state.samples.isEmpty()) Text("No recorded battery readings for this day.") else {
            val data = state.insights
            DetailRow("Average charging power", "${formatTrendNumber(state.directional.charging.averagePowerW)} W")
            DetailRow("Peak charging power", "${formatTrendNumber(state.directional.charging.peakPowerW)} W")
            DetailRow("Average discharge load", "${formatTrendNumber(state.directional.discharging.averagePowerW)} W")
            DetailRow("Peak discharge load", "${formatTrendNumber(data.peakDischargePowerW)} W")
            DetailRow("Idle / near zero", analyticsDuration(state.directional.idleMs))
            DetailRow("Lowest pack voltage", data.minimumVoltageV?.let { "${formatTrendNumber(it)} V" } ?: "—")
            DetailRow("Maximum temperature", data.maximumTemperatureC?.let { "$it °C" } ?: "—")
            DetailRow("Largest cell delta", data.maximumCellDeltaV?.let { "${formatTrendNumber(it * 1000)} mV" } ?: "—")
            DetailRow("Recorded readings", state.samples.size.toString())
            state.batteryUsage?.let { cycle ->
                Text("Latest battery cycle as of ${analyticsTime(minOf(state.nowMs, state.date!!.dayWindow(zone).endEpochMs), zone)}", style = MaterialTheme.typography.labelMedium)
                Text("${cycle.startSocPct}% → ${cycle.endSocPct}% · ${analyticsEnergy(cycle.dischargedWh)} used", style = MaterialTheme.typography.bodySmall)
                Text("Active-use estimate to 20%: ${batteryUsageProjectionDisplay(cycle).value}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RecordedPeriods(day: DirectionalDayInsights, zone: ZoneId) {
    val all = (day.charging.segments.map { it to true } + day.discharging.segments.map { it to false }).sortedByDescending { it.first.startMs }
    var limit by rememberSaveable { mutableIntStateOf(20) }
    AnalyticsDisclosure("Recorded periods", "${all.size} charging / discharging periods") {
        if (all.isEmpty()) Text("No complete charging or discharging periods yet.")
        all.take(limit).forEach { (segment, charging) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${if (charging) "Charging" else "Discharging"} · ${analyticsTime(segment.startMs, zone)}–${analyticsTime(segment.endMs, zone)}", style = MaterialTheme.typography.bodyMedium, color = if (charging) AnalyticsLime else AnalyticsAmber)
                Text("${analyticsEnergy(segment.energyWh)} · ${analyticsDuration(segment.durationMs)} · ${formatTrendNumber(segment.averagePowerW)} W average", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (limit < all.size) TextButton(onClick = { limit += 20 }) { Text("Show more periods") }
    }
}

@Composable
private fun AnalyticsValueDetails(report: SolarReport, allTime: SolarReport, settings: EnergySettings, onSettings: () -> Unit) {
    AnalyticsDisclosure("Value & payback", "Estimates at your current tariff") {
        DetailRow("Solar charging value", if (report.hasData) analyticsMoney(settings.potentialSolarValueFor(report.amount.chargedWh), settings) else "—", AnalyticsLime)
        DetailRow("Discharge savings", if (report.hasData) analyticsMoney(settings.savingsFor(report.amount.usedWh), settings) else "—", AnalyticsAmber)
        Text("Each estimate uses its recorded energy × your current tariff. The two amounts are separate and are not added together. Energy is measured at the battery; savings are an estimate of displaced grid energy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("Since tracking began", style = MaterialTheme.typography.labelLarge)
        DetailRow("Estimated discharge savings", if (allTime.hasData) analyticsMoney(settings.savingsFor(allTime.amount.usedWh), settings) else "—")
        if (allTime.hasData && settings.systemCost != null && settings.systemCost > 0 && settings.tariffPerKwh != null) {
            DetailRow("System cost", analyticsMoney(settings.systemCost, settings))
            DetailRow("Cost recovered", "${"%.1f".format(settings.paybackPercent(allTime.amount.usedWh))}%")
            DetailRow("Remaining to recover", analyticsMoney((settings.systemCost - settings.savingsFor(allTime.amount.usedWh)!!).coerceAtLeast(0.0), settings))
        } else Text("Set a tariff and positive system cost to see estimated payback.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onSettings) { Text("Tariff & system settings") }
    }
}

@Composable
private fun DetailRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    if (LocalDensity.current.fontScale >= 1.3f) Column { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); Text(value, color = color, fontWeight = FontWeight.SemiBold) }
    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold) }
}

internal fun analyticsDuration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    return when { seconds in 1..59 -> "<1m"; minutes < 60 -> "${minutes}m"; else -> "${minutes / 60}h ${minutes % 60}m" }
}
internal fun analyticsTime(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun analyticsMoney(value: Double?, settings: EnergySettings): String {
    if (value == null) return "Set tariff"
    return NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(settings.currencyCode); minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(value)
}
