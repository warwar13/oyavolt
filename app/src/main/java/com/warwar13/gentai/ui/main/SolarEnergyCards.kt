package com.warwar13.gentai.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import com.warwar13.gentai.analytics.EnergyPreferences
import com.warwar13.gentai.analytics.EnergySettings
import com.warwar13.gentai.analytics.DirectionalAnalytics
import com.warwar13.gentai.analytics.DirectionalDayInsights
import com.warwar13.gentai.analytics.SolarChartBar
import com.warwar13.gentai.analytics.SolarPeriod
import com.warwar13.gentai.analytics.SolarReport
import com.warwar13.gentai.analytics.SolarReports
import com.warwar13.gentai.data.SolarEnergyRepository
import com.warwar13.gentai.data.GentaiTelemetryRepository
import com.warwar13.gentai.data.TelemetryDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
private fun rememberEnergyClock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun SolarHomeCard() {
    val context = LocalContext.current
    val preferences = remember { EnergyPreferences.get(context) }
    val repository = remember { SolarEnergyRepository(TelemetryDatabase.get(context)) }
    val telemetryRepository = remember { GentaiTelemetryRepository(TelemetryDatabase.get(context)) }
    val settings by preferences.settings.collectAsStateWithLifecycle()
    val days by remember(repository) { repository.observeDays() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val now = rememberEnergyClock()
    LaunchedEffect(settings.trackingStartMs) {
        if (preferences.rebuildPending) {
            GentaiTelemetryRepository(TelemetryDatabase.get(context)).rebuildSolarRollups(settings)
            preferences.markRebuilt(settings.trackingStartMs)
        }
    }
    val today = remember(now, settings.reportingZoneId) { java.time.Instant.ofEpochMilli(now).atZone(settings.zone).toLocalDate() }
    val month = SolarReports.make(days, settings, SolarPeriod.MONTH, today, now)
    var showJournalInfo by remember { mutableStateOf(false) }
    val todayAnalytics by produceState<DirectionalDayInsights?>(initialValue = null, today, settings.reportingZoneId, settings.trackingStartMs) {
        val start = maxOf(today.atStartOfDay(settings.zone).toInstant().toEpochMilli(), settings.trackingStartMs)
        while (true) {
            value = DirectionalAnalytics.analyse(telemetryRepository.recordedAnalyticsSamplesBetween(start, System.currentTimeMillis()))
            delay(60_000)
        }
    }

    if (showJournalInfo) {
        AlertDialog(
            onDismissRequest = { showJournalInfo = false },
            title = { Text("About these totals") },
            text = { Text("Recorded time is the share of tracked time covered by valid battery readings. Gaps, wall charging, and direct solar use outside the battery are not inferred. Charged Value and Used Saved are separate estimates at your configured tariff; they are not a measured bill reduction. Totals refresh about once a minute.") },
            confirmButton = { TextButton(onClick = { showJournalInfo = false }) { Text("Close") } },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Energy journal", null, Icons.Rounded.Bolt)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeTodayFlowCard(todayAnalytics ?: DirectionalDayInsights(), settings)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
                HomeMonthSummaryWithMoney(month, settings)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (month.expectedMs > 0) "Recorded ${percent(month.coveragePct)} of tracked time" else "No tracked time yet",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { showJournalInfo = true }, modifier = Modifier.width(48.dp).height(48.dp)) {
                        Icon(Icons.Rounded.Info, contentDescription = "Explain energy journal and coverage", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(20.dp).height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTodayFlowCard(analytics: DirectionalDayInsights, settings: EnergySettings) {
    val charging = analytics.charging
    val discharging = analytics.discharging
    HomeJournalPeriod("TODAY", listOf(
        HomeDirectionMetric("Charged", homeKwh(charging.energyWh, charging.durationMs > 0), homeMoney("Value", charging.durationMs > 0, settings.potentialSolarValueFor(charging.energyWh)), "Avg ${if (charging.durationMs > 0) "${oneDecimal(charging.averagePowerW)} W" else "N/A"}", Icons.Rounded.BatteryChargingFull, MaterialTheme.colorScheme.primary),
        HomeDirectionMetric("Used", homeKwh(discharging.energyWh, discharging.durationMs > 0), homeMoney("Saved", discharging.durationMs > 0, settings.savingsFor(discharging.energyWh)), "Avg ${if (discharging.durationMs > 0) "${oneDecimal(discharging.averagePowerW)} W" else "N/A"}", Icons.Rounded.Bolt, MaterialTheme.colorScheme.tertiary),
    ))
}

private data class HomeDirectionMetric(
    val label: String,
    val energy: String,
    val money: String,
    val average: String?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color,
)

internal fun homeKwh(wh: Double, measured: Boolean): String {
    if (!measured) return "N/A"
    val amount = wh / 1_000.0
    if (amount > 0.0 && amount < 0.0005) return "<0.001 kWh"
    val decimals = when { amount < 1.0 -> 3; amount < 100.0 -> 2; else -> 1 }
    return String.format(Locale.getDefault(), "%.${decimals}f kWh", amount)
}

private fun homeMoney(label: String, measured: Boolean, amount: Double?): String = when {
    !measured -> "$label N/A"
    amount == null -> "$label Set tariff"
    else -> "$label ${String.format(Locale.getDefault(), "₱%.2f", amount)}"
}

@Composable
private fun HomeJournalPeriod(period: String, metrics: List<HomeDirectionMetric>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(period, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (LocalDensity.current.fontScale >= 1.3f) {
            Column {
                metrics.forEachIndexed { index, metric ->
                    HomeDirectionColumn(metric, Modifier.fillMaxWidth())
                    if (index < metrics.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                HomeDirectionColumn(metrics[0], Modifier.weight(1f))
                VerticalDivider(Modifier.height(84.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                HomeDirectionColumn(metrics[1], Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeDirectionColumn(metric: HomeDirectionMetric, modifier: Modifier) {
    Column(modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(metric.icon, contentDescription = null, tint = metric.accent, modifier = Modifier.width(16.dp).height(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(metric.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(metric.energy, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(metric.money, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = metric.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        metric.average?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun HomeMonthSummary(report: SolarReport, settings: EnergySettings) {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("This month", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (!report.hasData) {
            Text("No recorded energy data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Used ${kwh(report.amount.usedWh)}  •  Charged ${kwh(report.amount.chargedWh)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Saved ${settings.savingsFor(report.amount.usedWh)?.let { money(it, settings) } ?: "Set grid price"}  •  ${percent(report.coveragePct)} coverage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeMonthSummaryWithMoney(report: SolarReport, settings: EnergySettings) {
    HomeJournalPeriod("THIS MONTH", listOf(
        HomeDirectionMetric("Charged", homeKwh(report.amount.chargedWh, report.amount.chargingMs > 0), homeMoney("Value", report.amount.chargingMs > 0, settings.potentialSolarValueFor(report.amount.chargedWh)), null, Icons.Rounded.BatteryChargingFull, MaterialTheme.colorScheme.primary),
        HomeDirectionMetric("Used", homeKwh(report.amount.usedWh, report.amount.dischargingMs > 0), homeMoney("Saved", report.amount.dischargingMs > 0, settings.savingsFor(report.amount.usedWh)), null, Icons.Rounded.Bolt, MaterialTheme.colorScheme.tertiary),
    ))
}

@Composable
private fun HomeMonthMetric(title: String, energy: String, moneyText: String, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(energy, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(moneyText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomePeriodRow(label: String, report: SolarReport, settings: EnergySettings) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        if (!report.hasData) {
            Text("No recorded energy data • coverage ${percent(report.coveragePct)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Used ${kwh(report.amount.usedWh)}  •  Charged ${kwh(report.amount.chargedWh)}")
            Text("Estimated saved ${settings.savingsFor(report.amount.usedWh)?.let { money(it, settings) } ?: "Set grid price in Settings"}  •  ${percent(report.coveragePct)} coverage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SolarAnalyticsCard() {
    val context = LocalContext.current
    val preferences = remember { EnergyPreferences.get(context) }
    val repository = remember { SolarEnergyRepository(TelemetryDatabase.get(context)) }
    val settings by preferences.settings.collectAsStateWithLifecycle()
    val days by remember(repository) { repository.observeDays() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val now = rememberEnergyClock()
    val today = remember(now, settings.reportingZoneId) { java.time.Instant.ofEpochMilli(now).atZone(settings.zone).toLocalDate() }
    var period by remember { mutableStateOf(SolarPeriod.MONTH) }
    var anchor by remember { mutableStateOf(today) }
    var hourly by remember { mutableStateOf<List<SolarChartBar>>(emptyList()) }
    LaunchedEffect(period, anchor, days, settings, now) {
        hourly = if (period == SolarPeriod.DAY) repository.hourlyFor(anchor, settings, now) else emptyList()
    }
    val report = SolarReports.make(days, settings, period, anchor, now, hourly)
    val allTime = SolarReports.make(days, settings, SolarPeriod.ALL_TIME, today, now)
    val canGoForward = when (period) {
        SolarPeriod.DAY -> anchor < today
        SolarPeriod.MONTH -> anchor.withDayOfMonth(1) < today.withDayOfMonth(1)
        SolarPeriod.YEAR -> anchor.year < today.year
        SolarPeriod.ALL_TIME -> false
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Solar energy report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Measured battery flow, with charging assumed solar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SolarPeriod.entries.forEach { option ->
                    FilterChip(
                        selected = period == option,
                        onClick = { period = option; anchor = today },
                        label = { Text(when (option) { SolarPeriod.DAY -> "Day"; SolarPeriod.MONTH -> "Month"; SolarPeriod.YEAR -> "Year"; SolarPeriod.ALL_TIME -> "All" }) },
                    )
                }
            }
            if (period != SolarPeriod.ALL_TIME) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = { anchor = when (period) { SolarPeriod.DAY -> anchor.minusDays(1); SolarPeriod.MONTH -> anchor.minusMonths(1); SolarPeriod.YEAR -> anchor.minusYears(1); SolarPeriod.ALL_TIME -> anchor } }) { Icon(Icons.Rounded.ChevronLeft, "Previous period") }
                    Text(periodLabel(period, anchor), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { anchor = when (period) { SolarPeriod.DAY -> anchor.plusDays(1); SolarPeriod.MONTH -> anchor.plusMonths(1); SolarPeriod.YEAR -> anchor.plusYears(1); SolarPeriod.ALL_TIME -> anchor } }, enabled = canGoForward) { Icon(Icons.Rounded.ChevronRight, "Next period") }
                }
            }
            if (!report.hasData) {
                Text("No recorded energy data for this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Battery used  ${kwh(report.amount.usedWh)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Assumed solar to battery  ${kwh(report.amount.chargedWh)}")
                Text("Charging ${hours(report.amount.chargingMs)}  •  Discharging ${hours(report.amount.dischargingMs)}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeMonthMetric(
                        title = "Charging value",
                        energy = "Value",
                        moneyText = settings.potentialSolarValueFor(report.amount.chargedWh)?.let { money(it, settings) } ?: "Set grid price",
                        modifier = Modifier.weight(1f),
                    )
                    HomeMonthMetric(
                        title = "Discharging value",
                        energy = "Saved",
                        moneyText = settings.savingsFor(report.amount.usedWh)?.let { money(it, settings) } ?: "Set grid price",
                        modifier = Modifier.weight(1f),
                    )
                }
                SolarBarChart(report.bars)
            }
            Text("Coverage ${percent(report.coveragePct)} • observed ${hours(report.amount.observedMs)} • missing ${hours(report.missingMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Battery-side DC estimate at current grid price; wall charging and direct solar loads are not measured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SolarRoiCard(allTime, settings)
    }
}

@Composable
private fun SolarRoiCard(report: SolarReport, settings: EnergySettings) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Solar setup payback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val saved = settings.savingsFor(report.amount.usedWh)
            val cost = settings.systemCost?.takeIf { it > 0.0 }
            if (saved == null || cost == null) {
                Text("Set a grid price and positive system cost in Settings to see estimated payback.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Estimated saved since tracking began  ${money(saved, settings)}")
                Text("System cost  ${money(cost, settings)}")
                Text("Payback  ${percent(settings.paybackPercent(report.amount.usedWh) ?: 0.0)}")
                Text("Remaining to break even  ${money((cost - saved).coerceAtLeast(0.0), settings)}")
                Text("Net ROI  ${percent(settings.netRoiPercent(report.amount.usedWh) ?: 0.0)}")
                Text("Based on recorded battery discharge and current flat tariff; this is not a measured bill reduction.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SolarBarChart(bars: List<SolarChartBar>) {
    if (bars.isEmpty()) return
    val usedColor = MaterialTheme.colorScheme.primary
    val chargedColor = MaterialTheme.colorScheme.tertiary
    val maximum = bars.maxOf { maxOf(it.usedWh, it.chargedWh) }.coerceAtLeast(1.0)
    Text("Energy by period  •  used (green)  •  charged (amber)  •  gray = unmeasured", style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(135.dp)) {
        val group = size.width / bars.size
        val barWidth = (group * 0.31f).coerceAtLeast(1f)
        bars.forEachIndexed { index, bar ->
            val x = index * group
            if (bar.observedMs == 0L) drawRect(Color.Gray.copy(alpha = 0.65f), topLeft = Offset(x + group * 0.25f, size.height - 3.dp.toPx()), size = Size(group * 0.5f, 3.dp.toPx()))
            val useHeight = (bar.usedWh / maximum * size.height).toFloat()
            val chargeHeight = (bar.chargedWh / maximum * size.height).toFloat()
            drawRect(usedColor, topLeft = Offset(x + group * 0.17f, size.height - useHeight), size = Size(barWidth, useHeight))
            drawRect(chargedColor, topLeft = Offset(x + group * 0.52f, size.height - chargeHeight), size = Size(barWidth, chargeHeight))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(bars.first().label, style = MaterialTheme.typography.labelSmall)
        if (bars.size > 2) Text(bars[bars.size / 2].label, style = MaterialTheme.typography.labelSmall)
        if (bars.size > 1) Text(bars.last().label, style = MaterialTheme.typography.labelSmall)
    }
    Text("Peak bucket ${kwh(maximum)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun periodLabel(period: SolarPeriod, date: LocalDate): String = when (period) {
    SolarPeriod.DAY -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    SolarPeriod.MONTH -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    SolarPeriod.YEAR -> date.year.toString()
    SolarPeriod.ALL_TIME -> "All time"
}

private fun kwh(wh: Double): String = String.format(Locale.getDefault(), "%.3f kWh", wh / 1_000.0)
private fun oneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
private fun money(value: Double, settings: EnergySettings): String = String.format(Locale.getDefault(), "₱%.2f", value)
private fun financialLabel(label: String, hasData: Boolean, value: Double?, settings: EnergySettings): String =
    if (!hasData) "$label N/A" else "$label ${value?.let { money(it, settings) } ?: "Set grid price"}"
private fun percent(value: Double): String = String.format(Locale.getDefault(), "%.1f%%", value)
private fun hours(ms: Long): String = String.format(Locale.getDefault(), "%.1f h", ms / 3_600_000.0)
