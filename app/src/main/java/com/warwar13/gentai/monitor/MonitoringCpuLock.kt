package com.warwar13.gentai.monitor

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock

internal fun monitoringCpuLease(context: Context): MonitoringWakeLease {
    val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OyaVolt:BatteryMonitoring")
        .apply { setReferenceCounted(false) }
    return MonitoringWakeLease(
        clockMs = SystemClock::elapsedRealtime,
        isHeld = { lock.isHeld },
        acquire = { timeout -> lock.acquire(timeout) },
        release = { lock.release() },
    )
}
