package com.warwar13.gentai.monitor

import com.warwar13.gentai.protocol.ReadCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorPolicyTest {
    @Test fun `monitoring notification exists only while service monitoring is active`() {
        assertFalse(shouldShowMonitoringNotification(false, MonitorState.Idle))
        assertFalse(shouldShowMonitoringNotification(false, MonitorState.Connecting))
        assertFalse(shouldShowMonitoringNotification(true, MonitorState.Idle))
        assertTrue(shouldShowMonitoringNotification(true, MonitorState.Connecting))
        assertTrue(shouldShowMonitoringNotification(true, MonitorState.Connected(12.8, -1.0)))
    }

    @Test fun `poll plan requests telemetry only and cells every third poll`() {
        assertEquals(listOf(ReadCommand.BATTERY_INFO, ReadCommand.RUNNING_STATUS, ReadCommand.CELL_VOLTAGES), MonitorPolicy.commandsForPoll(0))
        assertEquals(listOf(ReadCommand.BATTERY_INFO, ReadCommand.RUNNING_STATUS), MonitorPolicy.commandsForPoll(1))
    }
    @Test fun `reconnect backoff is bounded`() {
        assertEquals(1_000L, MonitorPolicy.reconnectDelayMs(0))
        assertEquals(16_000L, MonitorPolicy.reconnectDelayMs(4))
        assertEquals(30_000L, MonitorPolicy.reconnectDelayMs(99))
    }
}
