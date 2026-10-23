package com.warwar13.gentai.monitor

import com.warwar13.gentai.analytics.FlowRecordingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticFlowRecorderTest {
    private fun reading(second: Long, amps: Double) = AutomaticFlowRecorder.Reading(second * 1_000, amps, second)

    @Test fun `starts only after two active readings and ignores idle`() {
        val recorder = AutomaticFlowRecorder<Long>()
        assertTrue(recorder.accept(reading(0, 0.0)).isEmpty())
        assertTrue(recorder.accept(reading(5, 1.0)).isEmpty())
        assertEquals(AutomaticFlowRecorder.Action.Start(FlowRecordingMode.CHARGING, reading(5, 1.0), reading(10, 1.0)), recorder.accept(reading(10, 1.0)).single())
        assertTrue(recorder.accept(reading(15, 0.0)).isEmpty())
        assertEquals(AutomaticFlowRecorder.Action.End(10_000, "Idle"), recorder.accept(reading(20, 0.0)).single())
        assertTrue(recorder.accept(reading(25, 0.0)).isEmpty())
    }

    @Test fun `one noisy reading creates a boundary but no wrong-direction row`() {
        val recorder = AutomaticFlowRecorder<Long>()
        recorder.accept(reading(0, -2.0))
        recorder.accept(reading(5, -2.0))
        assertTrue(recorder.accept(reading(10, 1.0)).isEmpty())
        assertEquals(AutomaticFlowRecorder.Action.Append(reading(15, -2.0), true), recorder.accept(reading(15, -2.0)).single())
    }

    @Test fun `direction change and disconnect split records`() {
        val recorder = AutomaticFlowRecorder<Long>()
        recorder.accept(reading(0, 2.0))
        recorder.accept(reading(5, 2.0))
        recorder.accept(reading(10, -2.0))
        assertEquals(listOf(AutomaticFlowRecorder.Action.End(5_000, "Direction changed"), AutomaticFlowRecorder.Action.Start(FlowRecordingMode.DISCHARGING, reading(10, -2.0), reading(15, -2.0))), recorder.accept(reading(15, -2.0)))
        assertEquals(AutomaticFlowRecorder.Action.End(15_000, "Disconnected"), recorder.disconnect().single())
        assertTrue(recorder.accept(reading(20, -2.0)).isEmpty())
        assertTrue(recorder.accept(reading(25, -2.0)).single() is AutomaticFlowRecorder.Action.Start)
    }
}
