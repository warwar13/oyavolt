package com.warwar13.gentai.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.warwar13.gentai.analytics.SolarPeriod
import com.warwar13.gentai.analytics.SolarReport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min

@Composable
internal fun AnalyticsEnergyBars(report: SolarReport, filter: AnalyticsFilter, selectedTime: Long?, onSelect: (Long?) -> Unit, zone: ZoneId, plotHeight: Dp) {
    val bars = report.bars
    val selectedIndex = selectedTime?.let { time -> bars.indexOfFirst { it.startEpochMs == time }.takeIf { it >= 0 } }
    val rawMaximum = bars.maxOfOrNull { maxOf(it.chargedWh, it.usedWh) } ?: 0.0
    val divisor = if (rawMaximum >= 1000) 1000.0 else 1.0
    val unit = if (divisor == 1000.0) "kWh" else "Wh"
    val maximum = analyticsNiceMaximum(rawMaximum / divisor)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fontScale = LocalDensity.current.fontScale
    val axisWidth = (44 * fontScale).dp.coerceAtMost(96.dp)
    fun selectAt(x: Float, width: Float) {
        if (bars.isNotEmpty() && width > 0) onSelect(bars[(x / width * bars.size).toInt().coerceIn(0, bars.lastIndex)].startEpochMs)
    }
    EnergyLegend()
    if (bars.isEmpty()) {
        Text("No recorded energy for this period.", Modifier.padding(vertical = 20.dp), color = muted)
        return
    }
    Text("$unit · paired energy amounts", style = MaterialTheme.typography.labelSmall, color = muted)
    Box(Modifier.fillMaxWidth().height(plotHeight)) {
        Canvas(
            Modifier.fillMaxSize().padding(start = axisWidth, top = 5.dp, bottom = 5.dp)
                .pointerInput(bars) { detectTapGestures { selectAt(it.x, size.width.toFloat()) } }
                .pointerInput(bars) { detectHorizontalDragGestures(onDragStart = { selectAt(it.x, size.width.toFloat()) }) { change, _ -> selectAt(change.position.x, size.width.toFloat()) } }
                .semantics { contentDescription = "Paired solar charging and battery discharge energy in $unit. ${bars.count { it.observedMs > 0 }} measured intervals, ${bars.count { it.inScope && it.observedMs == 0L }} unrecorded intervals. Use interval arrows below to inspect." },
        ) {
            repeat(5) { tick ->
                val y = size.height * tick / 4f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val group = size.width / bars.size
            val width = min(group * 0.31f, 15.dp.toPx())
            bars.forEachIndexed { index, bar ->
                val center = group * (index + 0.5f)
                if (selectedIndex == index) {
                    drawRect(Color.White.copy(alpha = 0.045f), Offset(index * group, 0f), Size(group, size.height))
                    drawLine(muted.copy(alpha = 0.6f), Offset(center, 0f), Offset(center, size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                }
                when (energySlotState(bar)) {
                    EnergySlotState.OUTSIDE_TRACKING -> Unit
                    EnergySlotState.MISSING -> drawLine(muted.copy(alpha = 0.4f), Offset(center - group * 0.2f, size.height - 1.dp.toPx()), Offset(center + group * 0.2f, size.height - 1.dp.toPx()), 1.5.dp.toPx())
                    EnergySlotState.MEASURED -> {
                        fun drawAmount(value: Double, x: Float, color: Color) {
                            if (value > 0) {
                                val height = (value / divisor / maximum * size.height).toFloat().coerceIn(1.dp.toPx(), size.height)
                                drawRoundRect(color, Offset(x - width / 2, size.height - height), Size(width, height), CornerRadius(min(3.dp.toPx(), width / 2)))
                            } else {
                                // A real zero is a colored mark at zero, never a fabricated positive bar.
                                drawLine(color.copy(alpha = 0.75f), Offset(x - width / 2, size.height), Offset(x + width / 2, size.height), 2.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                        if (filter != AnalyticsFilter.DISCHARGING) drawAmount(bar.chargedWh, center - width * 0.6f, AnalyticsLime)
                        if (filter != AnalyticsFilter.CHARGING) drawAmount(bar.usedWh, center + width * 0.6f, AnalyticsAmber)
                    }
                }
            }
        }
        Column(Modifier.fillMaxHeight().width(axisWidth - 4.dp), verticalArrangement = Arrangement.SpaceBetween) {
            repeat(5) { tick ->
                val value = maximum * (4 - tick) / 4
                val label = if (maximum >= 10 || value == 0.0) "%.0f".format(value) else if (maximum >= 1) "%.1f".format(value) else "%.2f".format(value)
                Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = axisWidth)) {
        val tickCount = min(if (fontScale >= 1.3f) 3 else 5, bars.size)
        repeat(tickCount) { tick ->
            val index = if (tickCount == 1) 0 else (bars.lastIndex * tick / (tickCount - 1.0)).toInt()
            Text(bars[index].label.take(5), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted, textAlign = when (tick) { 0 -> TextAlign.Start; tickCount - 1 -> TextAlign.End; else -> TextAlign.Center })
        }
    }
    Text("Gray marks: unrecorded · Blank: outside elapsed tracking time", style = MaterialTheme.typography.labelSmall, color = muted)
    Surface(Modifier.fillMaxWidth().heightIn(min = 100.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFF19251D)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (selectedIndex == null) "Inspect an interval" else intervalLabel(report, selectedIndex, zone), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { onSelect(bars[((selectedIndex ?: bars.size) - 1).coerceAtLeast(0)].startEpochMs) }, enabled = selectedIndex != 0, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ChevronLeft, "Previous interval") }
                IconButton(onClick = { onSelect(bars[((selectedIndex ?: -1) + 1).coerceAtMost(bars.lastIndex)].startEpochMs) }, enabled = selectedIndex != bars.lastIndex, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ChevronRight, "Next interval") }
            }
            if (selectedIndex == null) Text("Tap or drag the bars to compare charging and use.", style = MaterialTheme.typography.bodySmall, color = muted)
            else {
                val selected = bars[selectedIndex]
                when (energySlotState(selected)) {
                    EnergySlotState.OUTSIDE_TRACKING -> Text("Outside elapsed tracking time.", style = MaterialTheme.typography.bodySmall, color = muted)
                    EnergySlotState.MISSING -> Text("No recording for this interval. Energy is unknown.", style = MaterialTheme.typography.bodySmall, color = AnalyticsAmber)
                    EnergySlotState.MEASURED -> {
                        Text("Solar charged  ${analyticsEnergy(selected.chargedWh)}", style = MaterialTheme.typography.bodyMedium, color = AnalyticsLime)
                        Text("Battery used  ${analyticsEnergy(selected.usedWh)}", style = MaterialTheme.typography.bodyMedium, color = AnalyticsAmber)
                        val pct = if (selected.expectedMs > 0) (selected.observedMs * 100.0 / selected.expectedMs).coerceIn(0.0, 100.0) else 0.0
                        Text("Recorded ${analyticsDuration(selected.observedMs)} of ${analyticsDuration(selected.expectedMs)} · ${"%.0f".format(pct)}% coverage", style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                }
            }
        }
    }
}

private fun intervalLabel(report: SolarReport, index: Int, zone: ZoneId): String {
    val bar = report.bars[index]
    val start = bar.startEpochMs ?: return bar.label
    val date = Instant.ofEpochMilli(start).atZone(zone)
    return when (report.period) {
        SolarPeriod.DAY -> "${bar.label}–${analyticsTime(bar.endEpochMs ?: start, zone)}"
        SolarPeriod.MONTH -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        SolarPeriod.YEAR -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        SolarPeriod.ALL_TIME -> date.year.toString()
    }
}
