package com.warwar13.gentai.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.warwar13.gentai.analytics.TrendBucket
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

internal enum class TrendScale { SIGNED_ZERO, SOC, PADDED }

internal data class TrendBounds(val minimum: Double, val maximum: Double)

internal fun trendBounds(buckets: List<TrendBucket>, scale: TrendScale): TrendBounds {
    val low = buckets.minOfOrNull { it.minimum } ?: 0.0
    val high = buckets.maxOfOrNull { it.maximum } ?: 1.0
    if (scale == TrendScale.SOC) {
        val dataLow = low.coerceIn(0.0, 100.0)
        val dataHigh = high.coerceIn(0.0, 100.0)
        var minimum = (floor((dataLow - 1.0) / 5.0) * 5.0).coerceAtLeast(0.0)
        var maximum = (ceil((dataHigh + 1.0) / 5.0) * 5.0).coerceAtMost(100.0)
        if (maximum - minimum < 10.0) {
            minimum = (minimum - 5.0).coerceAtLeast(0.0)
            maximum = (minimum + 10.0).coerceAtMost(100.0)
            minimum = (maximum - 10.0).coerceAtLeast(0.0)
        }
        return TrendBounds(minimum, maximum)
    }
    if (scale == TrendScale.SIGNED_ZERO) {
        val upper = if (high > 0) niceCeiling(high * 1.08) else 0.0
        val lower = if (low < 0) -niceCeiling(-low * 1.08) else 0.0
        if (upper > 0 && lower < 0) {
            val limit = max(upper, -lower)
            return TrendBounds(-limit, limit)
        }
        return if (upper == lower) TrendBounds(0.0, 1.0) else TrendBounds(lower, upper)
    }
    val margin = max((high - low) * 0.12, if (high > 5.0) 1.0 else 0.1)
    return TrendBounds(low - margin, high + margin)
}

private fun niceCeiling(value: Double): Double {
    if (!value.isFinite() || value <= 0.0) return 1.0
    val base = 10.0.pow(floor(log10(value)))
    val scaled = value / base
    return (when {
        scaled <= 1.0 -> 1.0
        scaled <= 2.0 -> 2.0
        scaled <= 5.0 -> 5.0
        else -> 10.0
    }) * base
}

@Composable
internal fun ReadableTrendChart(
    buckets: List<TrendBucket>,
    startMs: Long,
    endMs: Long,
    unit: String,
    color: Color,
    scale: TrendScale,
    zoneId: ZoneId,
    selectedIndex: Int?,
    onSelect: (Int?, Long?) -> Unit,
    plotHeight: Dp = 190.dp,
    selectedTimeMs: Long? = null,
    negativeColor: Color? = null,
    readoutBelow: Boolean = false,
) {
    if (buckets.isEmpty()) return
    val bounds = remember(buckets, scale) { trendBounds(buckets, scale) }
    val span = (endMs - startMs).coerceAtLeast(1L).toDouble()
    val density = LocalDensity.current
    val axisWidth = (44 * density.fontScale).dp.coerceAtMost(96.dp)
    val axisTicks = if (density.fontScale >= 1.3f) 3 else 5
    val touchDistance = with(density) { 24.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)

    fun selectAt(touchX: Float) {
        if (widthPx <= 1f) return
        val target = startMs + ((touchX / widthPx).coerceIn(0f, 1f) * span).toLong()
        val nearest = buckets.indices.minByOrNull { abs(buckets[it].timestampMs - target) } ?: return
        val pointX = ((buckets[nearest].timestampMs - startMs) / span * widthPx).toFloat()
        val onConnectedLine = buckets.indices.drop(1).any { index ->
            !buckets[index].gapBefore && target in buckets[index - 1].timestampMs..buckets[index].timestampMs
        }
        if (abs(pointX - touchX) <= touchDistance || onConnectedLine) onSelect(nearest, target)
        else onSelect(null, null)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(plotHeight)) {
            Canvas(
                Modifier.fillMaxSize().padding(start = axisWidth, top = 4.dp, bottom = 4.dp)
                    .onSizeChanged { widthPx = it.width.toFloat() }
                    .pointerInput(buckets, widthPx) { detectTapGestures { selectAt(it.x) } }
                    .pointerInput(buckets, widthPx) {
                        detectHorizontalDragGestures(onDragStart = { selectAt(it.x) }) { change, _ -> selectAt(change.position.x) }
                    }
                    .semantics {
                        contentDescription = "${buckets.size} trend points, ${formatTrendNumber(bounds.minimum)} to ${formatTrendNumber(bounds.maximum)} $unit, ${buckets.count { it.gapBefore }} breaks"
                    },
            ) {
            fun x(timestampMs: Long) = ((timestampMs - startMs) / span * size.width).toFloat()
            fun y(value: Double) = (size.height -
                ((value - bounds.minimum) / (bounds.maximum - bounds.minimum).coerceAtLeast(0.000001) * size.height)).toFloat()
            repeat(5) { row ->
                val guideY = size.height * row / 4f
                drawLine(gridColor, Offset(0f, guideY), Offset(size.width, guideY), 1.dp.toPx())
            }
            if (negativeColor != null && bounds.minimum < 0 && bounds.maximum > 0) {
                drawLine(gridColor.copy(alpha = 0.85f), Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1.dp.toPx())
            }
            buckets.forEachIndexed { index, bucket ->
                if (index > 0 && !bucket.gapBefore) {
                    val previous = buckets[index - 1]
                    val from = Offset(x(previous.timestampMs), y(previous.average))
                    val to = Offset(x(bucket.timestampMs), y(bucket.average))
                    if (negativeColor != null && previous.average * bucket.average < 0) {
                        val fraction = (previous.average / (previous.average - bucket.average)).toFloat()
                        val zero = from + (to - from) * fraction
                        drawLine(if (previous.average < 0) negativeColor else color, from, zero, 2.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(if (bucket.average < 0) negativeColor else color, zero, to, 2.5.dp.toPx(), cap = StrokeCap.Round)
                    } else {
                        drawLine(if (negativeColor != null && (bucket.average < 0 || (bucket.average == 0.0 && previous.average < 0))) negativeColor else color, from, to, 2.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
            buckets.forEachIndexed { index, bucket ->
                val isolated = (index == 0 || bucket.gapBefore) &&
                    (index == buckets.lastIndex || buckets[index + 1].gapBefore)
                if (isolated) drawCircle((if (negativeColor != null && bucket.average < 0) negativeColor else color).copy(alpha = 0.65f), 1.75.dp.toPx(), Offset(x(bucket.timestampMs), y(bucket.average)))
            }
            selectedIndex?.takeIf { it in buckets.indices }?.let { index ->
                val point = Offset(x(buckets[index].timestampMs), y(buckets[index].average))
                drawLine(
                    color.copy(alpha = 0.55f),
                    Offset(point.x, 0f),
                    Offset(point.x, size.height),
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
                drawCircle(if (negativeColor != null && buckets[index].average < 0) negativeColor else color, 5.dp.toPx(), point)
            }
            }
            Column(
                Modifier.fillMaxHeight().width(axisWidth - 4.dp).padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(5) { index ->
                    val value = bounds.maximum - (bounds.maximum - bounds.minimum) * index / 4.0
                    Text(formatTrendNumber(value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            selectedIndex?.takeIf { !readoutBelow && it in buckets.indices }?.let { index ->
                val bucket = buckets[index]
                val fraction = ((bucket.timestampMs - startMs) / span).coerceIn(0.0, 1.0).toFloat()
                val tooltipWidth = 220.dp
                val plotWidth = (maxWidth - axisWidth).coerceAtLeast(1.dp)
                val pointX = axisWidth + plotWidth * fraction
                val tooltipX = if (fraction < 0.58f) {
                    (pointX + 8.dp).coerceAtMost(maxWidth - tooltipWidth)
                } else {
                    (pointX - tooltipWidth - 8.dp).coerceAtLeast(axisWidth)
                }
                val normalizedY = ((bucket.average - bounds.minimum) / (bounds.maximum - bounds.minimum).coerceAtLeast(0.000001)).coerceIn(0.0, 1.0)
                val pointY = plotHeight * (1f - normalizedY.toFloat())
                val tooltipY = if (pointY < plotHeight * 0.52f) (plotHeight - 86.dp).coerceAtLeast(8.dp) else 8.dp
                ReadableTrendReadout(
                    bucket = bucket,
                    unit = unit,
                    zoneId = zoneId,
                    selectedTimeMs = selectedTimeMs ?: bucket.timestampMs,
                    modifier = Modifier.width(tooltipWidth).offset(x = tooltipX, y = tooltipY),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = axisWidth), verticalAlignment = Alignment.CenterVertically) {
            repeat(axisTicks) { index ->
                val rawTime = startMs + ((endMs - startMs) * index / (axisTicks - 1.0)).toLong()
                val timestamp = if (index > 0 && index < axisTicks - 1) (rawTime + 30_000) / 60_000 * 60_000 else rawTime
                Text(
                    trendAxisTime(timestamp, zoneId, endMs - startMs),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when (index) { 0 -> TextAlign.Start; axisTicks - 1 -> TextAlign.End; else -> TextAlign.Center },
                    maxLines = 1,
                )
            }
        }
        Text("${formatTrendNumber(bounds.minimum)}–${formatTrendNumber(bounds.maximum)} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
        if (readoutBelow) {
            selectedIndex?.takeIf { it in buckets.indices }?.let { index ->
                val bucket = buckets[index]
                Text(
                    if (unit == "%") "Battery state of charge" else if (bucket.average > 0) "Charging" else if (bucket.average < 0) "Discharging" else "Zero measured flow",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unit != "%" && bucket.average < 0) negativeColor ?: color else color,
                )
                ReadableTrendReadout(bucket, unit, zoneId, selectedTimeMs ?: bucket.timestampMs, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun ReadableTrendReadout(bucket: TrendBucket, unit: String, zoneId: ZoneId, selectedTimeMs: Long = bucket.timestampMs, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f)), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val span = bucket.endMs - bucket.startMs
            val time = Instant.ofEpochMilli(bucket.startMs).atZone(zoneId).format(trendReadoutFormatter) +
                if (span > 0) "–${trendTime(bucket.endMs, zoneId, 0)}" else ""
            Text("$time · Avg ${formatTrendNumber(bucket.average)} $unit", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("Low ${formatTrendNumber(bucket.minimum)} · High ${formatTrendNumber(bucket.maximum)} $unit · ${bucket.sampleCount} readings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            bucket.nearestExact(selectedTimeMs)?.let { exact ->
                Text("Reading ${trendTime(exact.timestampMs, zoneId, 0)} · ${formatTrendNumber(exact.value)} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun formatTrendNumber(value: Double): String = when {
    abs(value) >= 100.0 -> String.format(java.util.Locale.getDefault(), "%.0f", value)
    abs(value) >= 10.0 -> String.format(java.util.Locale.getDefault(), "%.1f", value)
    else -> String.format(java.util.Locale.getDefault(), "%.2f", value)
}

private val trendClockFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val trendAxisClockFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val trendDateFormatter = DateTimeFormatter.ofPattern("MMM d")
private val trendReadoutFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
private fun trendTime(timestampMs: Long, zoneId: ZoneId, spanMs: Long): String =
    Instant.ofEpochMilli(timestampMs).atZone(zoneId).format(if (spanMs >= 24 * 60 * 60 * 1000L) trendDateFormatter else trendClockFormatter)

private fun trendAxisTime(timestampMs: Long, zoneId: ZoneId, spanMs: Long): String =
    Instant.ofEpochMilli(timestampMs).atZone(zoneId).format(if (spanMs > 36 * 60 * 60 * 1000L) trendDateFormatter else trendAxisClockFormatter)
