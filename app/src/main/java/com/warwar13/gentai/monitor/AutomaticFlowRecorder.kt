package com.warwar13.gentai.monitor

import com.warwar13.gentai.analytics.FlowRecordingAnalytics
import com.warwar13.gentai.analytics.FlowRecordingMode

/** Pure transition policy; the service applies these actions to Room in order. */
internal class AutomaticFlowRecorder<T> {
    data class Reading<T>(val timestampMs: Long, val currentA: Double, val value: T)

    sealed interface Action<out T> {
        data class Start<T>(val mode: FlowRecordingMode, val first: Reading<T>, val second: Reading<T>) : Action<T>
        data class Append<T>(val reading: Reading<T>, val gapBefore: Boolean) : Action<T>
        data class End(val timestampMs: Long, val reason: String) : Action<Nothing>
    }

    private var activeMode: FlowRecordingMode? = null
    private var candidateMode: FlowRecordingMode? = null
    private var candidate: Reading<T>? = null
    private var lastSavedAtMs: Long? = null

    fun accept(reading: Reading<T>): List<Action<T>> {
        val observed = FlowRecordingAnalytics.modeFor(reading.currentA)
        if (activeMode != null && observed == activeMode) {
            val gapBefore = candidate != null
            candidate = null
            candidateMode = null
            lastSavedAtMs = reading.timestampMs
            return listOf(Action.Append(reading, gapBefore))
        }
        val first = candidate
        if (first == null || candidateMode != observed) {
            candidate = reading
            candidateMode = observed
            return emptyList()
        }
        candidate = null
        candidateMode = null
        val actions = mutableListOf<Action<T>>()
        if (activeMode != null) {
            actions += Action.End(lastSavedAtMs ?: first.timestampMs, if (observed == null) "Idle" else "Direction changed")
            activeMode = null
            lastSavedAtMs = null
        }
        if (observed != null) {
            activeMode = observed
            lastSavedAtMs = reading.timestampMs
            actions += Action.Start(observed, first, reading)
        }
        return actions
    }

    fun disconnect(): List<Action<T>> {
        val end = if (activeMode != null) lastSavedAtMs?.let { Action.End(it, "Disconnected") } else null
        activeMode = null
        candidate = null
        candidateMode = null
        lastSavedAtMs = null
        return listOfNotNull(end)
    }

    fun stop(): List<Action<T>> {
        val end = if (activeMode != null) lastSavedAtMs?.let { Action.End(it, "Stopped") } else null
        activeMode = null
        candidate = null
        candidateMode = null
        lastSavedAtMs = null
        return listOfNotNull(end)
    }
}
