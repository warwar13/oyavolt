package com.warwar13.gentai.monitor

/** A bounded CPU lease, renewed by actual BLE-loop progress, never by a separate timer. */
internal class MonitoringWakeLease(
    private val clockMs: () -> Long,
    private val isHeld: () -> Boolean,
    private val acquire: (Long) -> Unit,
    private val release: () -> Unit,
) {
    private var enabled = true
    private var acquiredAtMs: Long? = null

    @Synchronized fun keepAwake() {
        if (!enabled) return
        val now = clockMs()
        val last = acquiredAtMs
        if (!isHeld() || last == null || now - last >= RENEW_AFTER_MS) {
            // Release first: old timed-acquire callbacks must not expire a renewed lease.
            releaseIfHeld()
            acquire(TIMEOUT_MS)
            acquiredAtMs = now
        }
    }

    @Synchronized fun pause() {
        releaseIfHeld()
    }

    @Synchronized fun stop() {
        enabled = false
        releaseIfHeld()
    }

    private fun releaseIfHeld() {
        if (isHeld()) release()
        acquiredAtMs = null
    }

    companion object {
        const val RENEW_AFTER_MS = 60_000L
        const val TIMEOUT_MS = 120_000L
    }
}
