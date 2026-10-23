package com.warwar13.gentai.analytics

import kotlin.math.ceil
import kotlin.math.max

/** Display-only readings. Summarized inputs can carry their original range and count. */
data class TrendReading(
    val timestampMs: Long,
    val value: Double,
    val startMs: Long = timestampMs,
    val endMs: Long = timestampMs,
    val minimum: Double = value,
    val maximum: Double = value,
    val sampleCount: Int = 1,
    val gapBefore: Boolean = false,
    val exact: Boolean = true,
)

data class TrendBucket(
    val startMs: Long,
    val endMs: Long,
    val timestampMs: Long,
    val average: Double,
    val minimum: Double,
    val maximum: Double,
    val sampleCount: Int,
    val gapBefore: Boolean,
    val readings: List<TrendReading>,
) {
    fun nearestExact(timestampMs: Long): TrendReading? = readings.asSequence()
        .filter { it.exact }
        .minByOrNull { kotlin.math.abs(it.timestampMs - timestampMs) }
}

/** Averages noisy readings for the plot without changing stored data or energy integration. */
object ReadableTrend {
    private const val DEFAULT_TARGET_BUCKETS = 48
    private const val DEFAULT_MIN_BUCKET_MS = 60_000L
    private const val SHORT_RUN_MS = 120_000L

    fun reduce(
        source: List<TrendReading>,
        startMs: Long,
        endMs: Long,
        maxContinuousGapMs: Long? = TelemetryAnalytics.MAX_INTEGRATION_GAP_MS,
        targetBuckets: Int = DEFAULT_TARGET_BUCKETS,
        minimumBucketMs: Long = DEFAULT_MIN_BUCKET_MS,
        preserveSignChanges: Boolean = false,
    ): List<TrendBucket> {
        val valid = source.filter {
            it.value.isFinite() && it.minimum.isFinite() && it.maximum.isFinite() && it.sampleCount > 0
        }.sortedBy { it.timestampMs }
        if (valid.isEmpty()) return emptyList()
        val span = (endMs - startMs).coerceAtLeast(1L)
        val rawMode = span < SHORT_RUN_MS
        val widthMs = max(minimumBucketMs.coerceAtLeast(1L), ceil(span / targetBuckets.coerceAtLeast(1).toDouble()).toLong())
        val result = mutableListOf<TrendBucket>()
        var members = mutableListOf<TrendReading>()
        var currentKey = Long.MIN_VALUE
        var breakBefore = false
        var previous: TrendReading? = null

        fun flush() {
            if (members.isEmpty()) return
            val count = members.sumOf { it.sampleCount }
            result += TrendBucket(
                startMs = members.first().startMs,
                endMs = members.last().endMs,
                timestampMs = members.first().startMs +
                    (members.last().endMs - members.first().startMs) / 2,
                average = members.sumOf { it.value * it.sampleCount } / count,
                minimum = members.minOf { it.minimum },
                maximum = members.maxOf { it.maximum },
                sampleCount = count,
                gapBefore = breakBefore,
                readings = members.toList(),
            )
            members = mutableListOf()
        }

        valid.forEachIndexed { index, reading ->
            val gap = previous != null && (
                reading.gapBefore ||
                    (maxContinuousGapMs != null && reading.timestampMs - previous.timestampMs > maxContinuousGapMs)
                )
            val key = if (rawMode) index.toLong() else Math.floorDiv(reading.timestampMs - startMs, widthMs)
            val signChange = preserveSignChanges && previous != null && previous.value.compareTo(0.0) != reading.value.compareTo(0.0)
            if (members.isNotEmpty() && (gap || key != currentKey || signChange)) flush()
            if (members.isEmpty()) {
                currentKey = key
                breakBefore = gap
            }
            members += reading
            previous = reading
        }
        flush()
        return result
    }
}
