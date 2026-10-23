package com.warwar13.gentai.capacity

import kotlin.math.abs

data class CapacityPoint(val timestampMs: Long, val voltageV: Double, val currentA: Double, val socPct: Int, val temperatureMaxC: Int? = null, val cellDeltaV: Double? = null, val segment: Int = 0)
data class CapacityResult(val chargedAh: Double, val dischargedAh: Double, val chargedWh: Double, val dischargedWh: Double, val observedMs: Long, val missingMs: Long, val averagePowerW: Double, val peakPowerW: Double, val minVoltageV: Double?, val startSocPct: Int?, val endSocPct: Int?) { val coveragePct get() = if (observedMs + missingMs == 0L) 0.0 else observedMs * 100.0 / (observedMs + missingMs) }
object CapacityAnalytics {
    const val MAX_GAP_MS = 30_000L
    fun analyse(points: List<CapacityPoint>): CapacityResult {
        val p = points.sortedBy { it.timestampMs }; var ca=0.0; var da=0.0; var cw=0.0; var dw=0.0; var observed=0L; var missing=0L
        for (i in 1 until p.size) { val a=p[i-1]; val b=p[i]; val dt=b.timestampMs-a.timestampMs; if (dt <= 0) continue; if (a.segment != b.segment || dt > MAX_GAP_MS) { missing += dt; continue }; val h=dt/3_600_000.0; ca += (a.currentA.coerceAtLeast(0.0)+b.currentA.coerceAtLeast(0.0))/2*h; da += ((-a.currentA).coerceAtLeast(0.0)+(-b.currentA).coerceAtLeast(0.0))/2*h; cw += ((a.voltageV*a.currentA).coerceAtLeast(0.0)+(b.voltageV*b.currentA).coerceAtLeast(0.0))/2*h; dw += ((-a.voltageV*a.currentA).coerceAtLeast(0.0)+(-b.voltageV*b.currentA).coerceAtLeast(0.0))/2*h; observed += dt }
        val averagePower = if (observed > 0) (cw + dw) / (observed / 3_600_000.0) else p.firstOrNull()?.let { abs(it.voltageV * it.currentA) } ?: 0.0
        return CapacityResult(ca,da,cw,dw,observed,missing,averagePower,p.maxOfOrNull { abs(it.voltageV*it.currentA) } ?: 0.0,p.minOfOrNull { it.voltageV },p.firstOrNull()?.socPct,p.lastOrNull()?.socPct)
    }
}
