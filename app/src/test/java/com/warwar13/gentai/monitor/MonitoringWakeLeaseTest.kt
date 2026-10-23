package com.warwar13.gentai.monitor

import org.junit.Assert.*
import org.junit.Test

class MonitoringWakeLeaseTest {
    private class FakeLock {
        var now = 0L
        var deadline = 0L
        var acquires = 0
        var releases = 0
        val held get() = deadline > now
        val lease = MonitoringWakeLease(
            clockMs = { now }, isHeld = { held },
            acquire = { timeout -> acquires++; deadline = now + timeout },
            release = { releases++; deadline = 0 },
        )
    }

    @Test fun `five second progress keeps lease without acquiring on every sample`() {
        val lock = FakeLock()
        repeat(12) { lock.now = it * 5_000L; lock.lease.keepAwake() }
        assertEquals(1, lock.acquires)
        assertEquals(0, lock.releases)
        assertTrue(lock.held)
        lock.now = 60_000
        lock.lease.keepAwake()
        assertEquals(2, lock.acquires)
        assertEquals(1, lock.releases)
        assertEquals(180_000L, lock.deadline)
    }

    @Test fun `stalled BLE loop has no independent renewal and lock expires`() {
        val lock = FakeLock()
        lock.lease.keepAwake()
        lock.now = MonitoringWakeLease.TIMEOUT_MS
        assertFalse(lock.held)
        lock.lease.keepAwake()
        assertEquals(2, lock.acquires)
        assertTrue(lock.held)
    }

    @Test fun `Bluetooth off releases lease and enable can acquire again`() {
        val lock = FakeLock()
        lock.lease.keepAwake()
        lock.lease.pause()
        assertFalse(lock.held)
        lock.now = 10_000
        lock.lease.keepAwake()
        assertTrue(lock.held)
        assertEquals(2, lock.acquires)
    }

    @Test fun `stop blocks late callbacks from reacquiring and is idempotent`() {
        val lock = FakeLock()
        lock.lease.keepAwake()
        lock.lease.stop()
        lock.lease.stop()
        lock.lease.keepAwake()
        lock.lease.pause()
        assertFalse(lock.held)
        assertEquals(1, lock.acquires)
        assertEquals(1, lock.releases)
    }

    @Test fun `stop before job starts prevents acquisition`() {
        val lock = FakeLock()
        lock.lease.stop()
        lock.lease.keepAwake()
        assertEquals(0, lock.acquires)
    }

    @Test fun `old monitor completion cannot release replacement monitor lease`() {
        val old = FakeLock()
        val replacement = FakeLock()
        old.lease.keepAwake()
        old.lease.stop()
        replacement.lease.keepAwake()
        old.lease.stop()
        assertTrue(replacement.held)
        assertFalse(old.held)
    }
}
