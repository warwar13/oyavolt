package com.warwar13.gentai.analytics

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

data class SolarReading(
    val timestampMs: Long,
    val powerW: Double,
    val sessionId: Long,
    val disconnectBefore: Boolean = false,
)

data class SolarEnergyAmount(
    val chargedWh: Double = 0.0,
    val usedWh: Double = 0.0,
    val observedMs: Long = 0,
    val chargingMs: Long = 0,
    val dischargingMs: Long = 0,
) {
    operator fun plus(other: SolarEnergyAmount) = SolarEnergyAmount(
        chargedWh + other.chargedWh,
        usedWh + other.usedWh,
        observedMs + other.observedMs,
        chargingMs + other.chargingMs,
        dischargingMs + other.dischargingMs,
    )
}

data class SolarEnergyPart(val startMs: Long, val amount: SolarEnergyAmount)

object SolarEnergyAnalytics {
    private const val MAX_GAP_MS = 30_000L

    fun dailyParts(a: SolarReading, b: SolarReading, trackingStartMs: Long, zone: ZoneId): List<SolarEnergyPart> =
        parts(a, b, trackingStartMs) { cursor ->
            Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        }

    fun hourlyParts(a: SolarReading, b: SolarReading, trackingStartMs: Long, zone: ZoneId): List<SolarEnergyPart> =
        parts(a, b, trackingStartMs) { cursor ->
            Instant.ofEpochMilli(cursor).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
        }

    private fun parts(
        a: SolarReading,
        b: SolarReading,
        trackingStartMs: Long,
        nextBoundary: (Long) -> Long,
    ): List<SolarEnergyPart> {
        val elapsed = b.timestampMs - a.timestampMs
        if (elapsed <= 0 || elapsed > MAX_GAP_MS || a.sessionId != b.sessionId || b.disconnectBefore || !a.powerW.isFinite() || !b.powerW.isFinite()) return emptyList()
        var cursor = maxOf(a.timestampMs, trackingStartMs)
        if (cursor >= b.timestampMs) return emptyList()
        val chargeWh = (a.powerW.coerceAtLeast(0.0) + b.powerW.coerceAtLeast(0.0)) / 2.0 * elapsed / 3_600_000.0
        val useWh = ((-a.powerW).coerceAtLeast(0.0) + (-b.powerW).coerceAtLeast(0.0)) / 2.0 * elapsed / 3_600_000.0
        val crossesZero = (a.powerW > 0.0 && b.powerW < 0.0) || (a.powerW < 0.0 && b.powerW > 0.0)
        val zeroAtMs = if (crossesZero) a.timestampMs + elapsed * (a.powerW / (a.powerW - b.powerW)) else 0.0
        val result = mutableListOf<SolarEnergyPart>()
        while (cursor < b.timestampMs) {
            val end = minOf(b.timestampMs, nextBoundary(cursor))
            check(end > cursor) { "Calendar boundary must advance" }
            val fraction = (end - cursor).toDouble() / elapsed
            val span = end - cursor
            val chargingMs = when {
                a.powerW >= 0.0 && b.powerW >= 0.0 -> if (a.powerW > 0.0 || b.powerW > 0.0) span else 0L
                a.powerW <= 0.0 && b.powerW <= 0.0 -> 0L
                a.powerW > 0.0 -> (minOf(end.toDouble(), zeroAtMs) - cursor).coerceAtLeast(0.0).roundToLong().coerceAtMost(span)
                else -> (end - maxOf(cursor.toDouble(), zeroAtMs)).coerceAtLeast(0.0).roundToLong().coerceAtMost(span)
            }
            val dischargingMs = if (a.powerW == 0.0 && b.powerW == 0.0) 0L else span - chargingMs
            result += SolarEnergyPart(cursor, SolarEnergyAmount(chargeWh * fraction, useWh * fraction, span, chargingMs, dischargingMs))
            cursor = end
        }
        return result
    }
}
