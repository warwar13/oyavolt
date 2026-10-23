package com.warwar13.gentai.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringReliabilityTest {
    @Test fun `repeated start does not duplicate a connection`() {
        val guard = DuplicateConnectionGuard()
        assertEquals(StartDecision.START, guard.requestStart("A"))
        assertEquals(StartDecision.ALREADY_RUNNING, guard.requestStart("A"))
        assertEquals(StartDecision.REPLACE_RUNNING, guard.requestStart("B"))
        guard.stopped()
        assertEquals(StartDecision.START, guard.requestStart("B"))
    }

    @Test fun `service recreation restores explicit active request`() {
        assertEquals("A", MonitoringRestorationPolicy.addressForServiceRecreation(PersistedMonitoringState(true, false, "A")))
        assertNull(MonitoringRestorationPolicy.addressForServiceRecreation(PersistedMonitoringState(false, true, "A")))
    }

    @Test fun `boot resume requires explicit setting and active request`() {
        assertNull(MonitoringRestorationPolicy.addressForBoot(PersistedMonitoringState(true, false, "A")))
        assertNull(MonitoringRestorationPolicy.addressForBoot(PersistedMonitoringState(false, true, "A")))
        assertEquals("A", MonitoringRestorationPolicy.addressForBoot(PersistedMonitoringState(true, true, "A")))
    }

    @Test fun `missing device never restores`() {
        assertNull(MonitoringRestorationPolicy.addressForBoot(PersistedMonitoringState(true, true, null)))
    }

    @Test fun `visible app auto records remembered battery once`() {
        val selected = PersistedMonitoringState(false, false, "A")
        assertFalse(shouldAutoRecord(selected, true, true, true, false))
        assertTrue(shouldAutoRecord(selected.copy(requested = true), true, true, true, false))
        assertFalse(shouldAutoRecord(selected, true, true, true, true))
        assertFalse(shouldAutoRecord(selected, true, true, false, false))
        assertFalse(shouldAutoRecord(selected, true, false, true, false))
        assertFalse(shouldAutoRecord(selected, false, true, true, false))
        assertFalse(shouldAutoRecord(selected.copy(deviceAddress = null), true, true, true, false))
    }

    @Test fun `Bluetooth unavailable waits while other errors back off`() {
        assertEquals(RecoveryAction.WaitForBluetooth, recoveryFor("Bluetooth is disabled", 3))
        assertEquals(RecoveryAction.Reconnect(4_000), recoveryFor("Out of range", 2))
    }
}
