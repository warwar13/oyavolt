package com.warwar13.gentai.monitor

import android.content.Context

data class PersistedMonitoringState(
    val requested: Boolean = false,
    val resumeAfterRestart: Boolean = false,
    val deviceAddress: String? = null,
)

class MonitoringPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read() = PersistedMonitoringState(
        requested = preferences.getBoolean(KEY_REQUESTED, false),
        resumeAfterRestart = preferences.getBoolean(KEY_RESUME, false),
        deviceAddress = preferences.getString(KEY_ADDRESS, null),
    )

    fun monitoringStarted(address: String) {
        preferences.edit().putBoolean(KEY_REQUESTED, true).putString(KEY_ADDRESS, address).apply()
    }

    fun deviceSelected(address: String) {
        preferences.edit().putString(KEY_ADDRESS, address).apply()
    }

    fun monitoringStopped() {
        preferences.edit().putBoolean(KEY_REQUESTED, false).apply()
    }

    fun setResumeAfterRestart(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_RESUME, enabled).apply()
    }

    companion object {
        private const val FILE_NAME = "monitoring_preferences"
        private const val KEY_REQUESTED = "monitoring_requested"
        private const val KEY_RESUME = "resume_after_restart"
        private const val KEY_ADDRESS = "selected_device_address"
    }
}

object MonitoringRestorationPolicy {
    fun addressForServiceRecreation(state: PersistedMonitoringState): String? =
        state.deviceAddress?.takeIf { state.requested }

    fun addressForBoot(state: PersistedMonitoringState): String? =
        state.deviceAddress?.takeIf { state.requested && state.resumeAfterRestart }
}

fun shouldAutoRecord(state: PersistedMonitoringState, hasBlePermission: Boolean, bluetoothEnabled: Boolean, appVisible: Boolean, alreadyAttempted: Boolean): Boolean =
    state.requested && state.deviceAddress != null && hasBlePermission && bluetoothEnabled && appVisible && !alreadyAttempted

enum class StartDecision { START, ALREADY_RUNNING, REPLACE_RUNNING }

class DuplicateConnectionGuard {
    private var activeAddress: String? = null

    fun requestStart(address: String): StartDecision = when (activeAddress) {
        null -> StartDecision.START.also { activeAddress = address }
        address -> StartDecision.ALREADY_RUNNING
        else -> StartDecision.REPLACE_RUNNING.also { activeAddress = address }
    }

    fun stopped() { activeAddress = null }
}

sealed interface RecoveryAction {
    data object WaitForBluetooth : RecoveryAction
    data class Reconnect(val delayMs: Long) : RecoveryAction
}

fun recoveryFor(message: String?, failures: Int): RecoveryAction =
    if (message == "Bluetooth is disabled" || message == "Bluetooth is unavailable") RecoveryAction.WaitForBluetooth
    else RecoveryAction.Reconnect(MonitorPolicy.reconnectDelayMs(failures))
