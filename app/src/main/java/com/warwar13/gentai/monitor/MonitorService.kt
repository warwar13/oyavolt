package com.warwar13.gentai.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.warwar13.gentai.MainActivity
import com.warwar13.gentai.R
import com.warwar13.gentai.capacity.CapacityPoint
import com.warwar13.gentai.capacity.CapacityTestRepository
import com.warwar13.gentai.data.GentaiTelemetryRepository
import com.warwar13.gentai.data.TelemetryDatabase
import com.warwar13.gentai.data.TelemetrySampleEntity
import com.warwar13.gentai.analytics.EnergyPreferences
import com.warwar13.gentai.analytics.FlowRecordingMode
import com.warwar13.gentai.protocol.BatteryInfo
import com.warwar13.gentai.protocol.RunningStatus
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface MonitorEvent {
    data class Reading(val info: BatteryInfo, val cells: List<Double>, val status: RunningStatus?, val timestampMs: Long, val firstAfterConnect: Boolean) : MonitorEvent
    data object Disconnected : MonitorEvent
    data object Stopped : MonitorEvent
}

private data class SavedReading(val event: MonitorEvent.Reading, val sampleId: Long)

class MonitorService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var monitor: GentaiBleMonitor? = null
    private var cpuLease: MonitoringWakeLease? = null
    private var activeFlowRecordingId: Long? = null
    @Volatile private var foregroundActive = false
    private val notificationLock = Any()
    private val guard = DuplicateConnectionGuard()
    private val preferences by lazy { MonitoringPreferences(applicationContext) }
    private val alertTracker by lazy { BmsAlertTracker() }
    private val socThresholdAlertTracker by lazy { SocThresholdAlertTracker() }
    private val repository by lazy { GentaiTelemetryRepository(TelemetryDatabase.get(applicationContext)) }
    private val energyPreferences by lazy { EnergyPreferences.get(applicationContext) }
    private val capacityTests by lazy { CapacityTestRepository(TelemetryDatabase.get(applicationContext)) }
    private val alertHistory by lazy { AlertHistoryRepository(TelemetryDatabase.get(applicationContext)) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var lastMonitoringNotificationText: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scope.launch {
            state.collect { current ->
                synchronized(notificationLock) {
                    val text = current.notificationText()
                    if (shouldShowMonitoringNotification(foregroundActive, current) && text != lastMonitoringNotificationText) {
                        notificationManager.notify(NOTIFICATION_ID, notification(text))
                        lastMonitoringNotificationText = text
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoringExplicitly()
            return START_NOT_STICKY
        }
        val address = when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
            else -> MonitoringRestorationPolicy.addressForServiceRecreation(preferences.read())
        } ?: run { stopSelf(); return START_NOT_STICKY }
        startForegroundCompat(notification("Connecting to battery"))
        synchronized(notificationLock) {
            foregroundActive = true
            lastMonitoringNotificationText = "Connecting to battery"
        }
        if (intent?.action == ACTION_START) {
            preferences.deviceSelected(address)
            preferences.monitoringStarted(address)
        }
        startSingleMonitor(address)
        return START_STICKY
    }

    private fun startSingleMonitor(address: String) {
        if (guard.requestStart(address) == StartDecision.ALREADY_RUNNING && monitorJob?.isActive == true) return
        val previousJob = monitorJob
        monitor?.close()
        previousJob?.cancel()
        cpuLease?.stop()
        val lease = monitoringCpuLease(applicationContext)
        cpuLease = lease
        monitorJob = scope.launch {
          try {
            lease.keepAwake()
            previousJob?.join()
            repository.beginMonitoringPeriod()
            repository.recoverOpenFlowRecordings()
            activeFlowRecordingId = null
            val connectionSessionId = repository.beginSession()
            val events = Channel<MonitorEvent>(Channel.UNLIMITED)
            val processor = scope.launch {
                val recorder = AutomaticFlowRecorder<SavedReading>()
                var lastSummaryAtMs = 0L
                suspend fun saveRaw(reading: MonitorEvent.Reading): Long {
                    val id = repository.recordForSession(
                        connectionSessionId, reading.info, reading.cells, reading.status, reading.timestampMs,
                        forceGap = reading.firstAfterConnect,
                        energySettings = energyPreferences.settings.value,
                    )
                    _lastSavedAtEpochMs.value = reading.timestampMs
                    return id
                }
                suspend fun link(sampleId: Long, recordingId: Long) {
                    repository.assignSampleToFlowRecording(sampleId, recordingId)
                }
                suspend fun markBoundary(sampleId: Long) {
                    repository.markSampleAsGap(sampleId)
                }
                suspend fun refreshIfDue(reading: MonitorEvent.Reading) {
                    val id = activeFlowRecordingId
                    if (id != null && lastSummaryAtMs != 0L && reading.timestampMs - lastSummaryAtMs >= 60_000L) {
                        repository.refreshFlowRecording(id)
                        lastSummaryAtMs = reading.timestampMs
                    }
                }
                for (event in events) {
                    try {
                        val actions = when (event) {
                            is MonitorEvent.Reading -> {
                                capacityTests.recoverInProgress()?.takeIf { it.state == "RUNNING" }?.let { test ->
                                    capacityTests.record(test, CapacityPoint(event.timestampMs, event.info.voltageV, event.info.currentA, event.info.socPct, maxOf(event.info.temperaturesC.ambient, event.info.temperaturesC.mos), event.cells.takeIf { it.isNotEmpty() }?.let { it.max() - it.min() }))
                                }
                                val sampleId = saveRaw(event)
                                recorder.accept(AutomaticFlowRecorder.Reading(event.timestampMs, event.info.currentA, SavedReading(event, sampleId)))
                            }
                            MonitorEvent.Disconnected -> recorder.disconnect()
                            MonitorEvent.Stopped -> recorder.stop()
                        }
                        for (action in actions) when (action) {
                            is AutomaticFlowRecorder.Action.Start -> {
                                activeFlowRecordingId = repository.beginFlowRecording(action.mode, action.first.timestampMs)
                                val recordingId = checkNotNull(activeFlowRecordingId)
                                link(action.first.value.sampleId, recordingId)
                                link(action.second.value.sampleId, recordingId)
                                repository.refreshFlowRecording(recordingId)
                                lastSummaryAtMs = action.second.timestampMs
                                _activeRecordingMode.value = action.mode
                            }
                            is AutomaticFlowRecorder.Action.Append -> {
                                val recordingId = activeFlowRecordingId
                                if (recordingId != null) {
                                    link(action.reading.value.sampleId, recordingId)
                                    if (action.gapBefore) markBoundary(action.reading.value.sampleId)
                                    refreshIfDue(action.reading.value.event)
                                }
                            }
                            is AutomaticFlowRecorder.Action.End -> {
                                activeFlowRecordingId?.let { repository.endFlowRecording(it, action.reason, action.timestampMs) }
                                activeFlowRecordingId = null
                                _activeRecordingMode.value = null
                            }
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Log.e("MonitorService", "Automatic telemetry save failed", error)
                        _activeRecordingMode.value = null
                        stopMonitoringExplicitly()
                        break
                    }
                }
            }
            val localMonitor = GentaiBleMonitor(applicationContext, address, _state, true, onBatteryInfo = { info, cells, status, firstAfterConnect ->
                val now = System.currentTimeMillis()
                showAlerts(
                    alertTracker.update(
                        socPct = info.socPct,
                        warnings = status?.warnings,
                        currentA = info.currentA,
                        cellsV = cells,
                        nowEpochMs = now,
                        voltageV = info.voltageV,
                    ),
                    info.socPct,
                )
                showSocThresholdAlerts(socThresholdAlertTracker.update(info.socPct, info.currentA))
                val cellMin = cells.minOrNull()
                val cellMax = cells.maxOrNull()
                _liveSample.value = TelemetrySampleEntity(
                    timestampEpochMs = now,
                    connectionSessionId = connectionSessionId,
                    voltageV = info.voltageV,
                    currentA = info.currentA,
                    powerW = info.voltageV * info.currentA,
                    socPct = info.socPct,
                    sohPct = info.sohPct,
                    remainingAh = info.remainingAh,
                    fullAh = info.fullAh,
                    cycleCount = info.cycles,
                    cellVoltagesV = cells.joinToString(","),
                    cellMinV = cellMin,
                    cellMaxV = cellMax,
                    cellDeltaV = if (cellMin == null || cellMax == null) null else cellMax - cellMin,
                    probeTemperaturesC = info.temperaturesC.probes.joinToString(","),
                    ambientTemperatureC = info.temperaturesC.ambient,
                    mosTemperatureC = info.temperaturesC.mos,
                    protectionBitfield = status?.bitfield,
                    balanceMask = status?.balanceMask,
                    disconnectedCellMask = status?.disconnectedCellMask,
                )
                events.trySend(MonitorEvent.Reading(info, cells.toList(), status, now, firstAfterConnect))
            }, onConnectionLost = { events.trySend(MonitorEvent.Disconnected) },
                onPollingActivity = { active -> if (active) lease.keepAwake() else lease.pause() })
            monitor = localMonitor
            try {
                localMonitor.run()
            } finally {
                withContext(NonCancellable) {
                    events.trySend(MonitorEvent.Stopped)
                    events.close()
                    processor.join()
                    repository.endSession("Connection ended")
                }
            }
          } catch (error: CancellationException) {
              throw error
          } catch (error: Exception) {
              Log.e("MonitorService", "Monitoring failed", error)
              _state.value = MonitorState.Failed("Recording stopped: ${error.message ?: "monitor error"}. Restart monitoring in Settings.")
              monitor?.close()
          } finally {
              lease.stop()
          }
        }
    }

    private fun stopMonitoringExplicitly() {
        cpuLease?.stop()
        preferences.monitoringStopped()
        guard.stopped()
        monitor?.close()
        monitor = null
        val previousJob = monitorJob
        previousJob?.cancel()
        monitorJob = null
        synchronized(notificationLock) {
            foregroundActive = false
            lastMonitoringNotificationText = null
            removeMonitoringNotification()
        }
        _state.value = MonitorState.Idle
        _liveSample.value = null
        _activeRecordingMode.value = null
        scope.launch {
            previousJob?.join()
            repository.endMonitoringPeriod("Stopped")
            stopSelf()
        }
    }

    override fun onDestroy() {
        cpuLease?.stop()
        synchronized(notificationLock) {
            foregroundActive = false
            lastMonitoringNotificationText = null
            removeMonitoringNotification()
        }
        monitor?.close()
        monitor = null
        monitorJob?.cancel()
        monitorJob = null
        activeFlowRecordingId = null
        _state.value = MonitorState.Idle
        _liveSample.value = null
        _activeRecordingMode.value = null
        serviceJob.cancel()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.recoverOpenFlowRecordings()
            repository.endSession("Service destroyed")
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun removeMonitoringNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannels() {
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_ID, "Battery monitoring", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(ALERT_CHANNEL_ID, "Battery warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Protection warnings reported by the battery BMS"
                },
            ),
        )
    }

    private fun showAlerts(alerts: List<BmsAlert>, socPct: Int) {
        if (alerts.isEmpty()) return
        if (alerts.none { it is BmsAlert.ProtectionWarnings }) return
        scope.launch { alertHistory.record(alerts, socPct) }
        val manager = getSystemService(NotificationManager::class.java)
        alerts.forEach { alert ->
            if (alert is BmsAlert.FullCharge) {
                val text = "${"%.2f".format(alert.voltageV)} V • ${alert.socPct}% SOC. Unplug the charger now."
                manager.notify(
                    FULL_CHARGE_NOTIFICATION_ID,
                    fullChargeNotification("Battery full — unplug charger", text),
                )
                return@forEach
            }
            val (id, title, text) = when (alert) {
                is BmsAlert.LowSoc -> Triple(LOW_SOC_25_NOTIFICATION_ID, "Battery charge is low", "SOC is ${alert.socPct}%. Charge the battery soon.")
                is BmsAlert.ProtectionWarnings -> Triple(BMS_WARNING_NOTIFICATION_ID, "Battery protection warning", alert.warnings.joinToString())
                is BmsAlert.FullCharge -> error("Handled above")
                is BmsAlert.ChargingCellRisk -> Triple(
                    CELL_IMBALANCE_NOTIFICATION_ID,
                    "Unplug charger — C${alert.highCellNumber} high",
                    "C${alert.highCellNumber} ${"%.3f".format(alert.highCellVoltageV)} V • ${"%.0f".format(alert.deltaV * 1_000)} mV cell spread. Unplug the charger now.",
                )
            }
            manager.notify(id, alertNotification(title, text))
        }
    }

    private fun showSocThresholdAlerts(alerts: List<SocThresholdAlert>) {
        if (alerts.isEmpty()) return
        val manager = getSystemService(NotificationManager::class.java)
        alerts.forEach { alert ->
            val urgent = alert.thresholdPct <= 20
            val title = if (urgent) {
                "Battery very low: ${alert.thresholdPct}% remaining"
            } else {
                "Battery low: ${alert.thresholdPct}% remaining"
            }
            val action = if (urgent) "Turn off the inverter soon." else "Prepare to turn off the inverter."
            val text = "SOC is ${alert.observedSocPct}% while discharging. $action"
            manager.notify(
                if (urgent) LOW_SOC_20_NOTIFICATION_ID else LOW_SOC_25_NOTIFICATION_ID,
                alertNotification(title, text),
            )
        }
    }

    private fun fullChargeNotification(title: String, text: String): Notification {
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, FULL_CHARGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(activity)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .build()
    }

    private fun alertNotification(title: String, text: String): Notification {
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(activity)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
    }
    private fun notification(text: String): Notification {
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification).setContentTitle("OyaVolt monitoring").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(activity).addAction(0, "Stop", stop).setOnlyAlertOnce(true).setOngoing(true).build()
    }

    companion object {
        const val ACTION_START = "com.warwar13.gentai.monitor.START"
        const val ACTION_STOP = "com.warwar13.gentai.monitor.STOP"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        private const val CHANNEL_ID = "gentai_monitoring"
        private const val ALERT_CHANNEL_ID = "gentai_battery_warnings_v1"
        private const val FULL_CHARGE_CHANNEL_ID = "gentai_full_charge_alarm_v1"
        private const val NOTIFICATION_ID = 1001
        private const val LOW_SOC_25_NOTIFICATION_ID = 1002
        private const val BMS_WARNING_NOTIFICATION_ID = 1003
        private const val CELL_IMBALANCE_NOTIFICATION_ID = 1004
        private const val FULL_CHARGE_NOTIFICATION_ID = 1005
        private const val LOW_SOC_20_NOTIFICATION_ID = 1006
        private val _state = MutableStateFlow<MonitorState>(MonitorState.Idle)
        val state: StateFlow<MonitorState> = _state
        private val _liveSample = MutableStateFlow<TelemetrySampleEntity?>(null)
        val liveSample: StateFlow<TelemetrySampleEntity?> = _liveSample
        private val _lastSavedAtEpochMs = MutableStateFlow<Long?>(null)
        val lastSavedAtEpochMs: StateFlow<Long?> = _lastSavedAtEpochMs
        private val _activeRecordingMode = MutableStateFlow<FlowRecordingMode?>(null)
        val activeRecordingMode: StateFlow<FlowRecordingMode?> = _activeRecordingMode
        fun start(context: Context, address: String) = context.startForegroundService(Intent(context, MonitorService::class.java).setAction(ACTION_START).putExtra(EXTRA_DEVICE_ADDRESS, address))
        fun stop(context: Context) = context.startService(Intent(context, MonitorService::class.java).setAction(ACTION_STOP))
    }
}

sealed interface MonitorState {
    data object Idle : MonitorState
    data object Connecting : MonitorState
    data object BluetoothUnavailable : MonitorState
    data class Connected(
        val voltageV: Double?,
        val currentA: Double?,
        val socPct: Int? = null,
        val livePowerW: Double? = null,
        val estimate: BatteryTimeEstimate? = null,
        val recording: Boolean = true,
        val status: String = "Monitoring",
    ) : MonitorState
    data class Reconnecting(val delayMs: Long, val detail: String) : MonitorState
    data class Failed(val detail: String) : MonitorState
}

private fun MonitorState.legacyNotificationText() = when (this) {
    MonitorState.Idle -> "Stopped"
    MonitorState.Connecting -> "Connecting to battery"
    MonitorState.BluetoothUnavailable -> "Bluetooth is off; waiting to reconnect"
    is MonitorState.Connected -> listOfNotNull(
        "Monitoring battery",
    ).ifEmpty { listOf("Monitoring") }.joinToString(" · ")
    is MonitorState.Reconnecting -> "Connection lost; retrying in ${delayMs / 1000}s"
    is MonitorState.Failed -> detail
}

internal fun MonitorState.notificationText() = when (this) {
    MonitorState.Idle -> "Stopped"
    MonitorState.Connecting -> "Connecting to battery"
    MonitorState.BluetoothUnavailable -> "Bluetooth is off; waiting to reconnect"
    is MonitorState.Connected -> "Monitoring battery"
    is MonitorState.Reconnecting -> "Connection lost; retrying in ${delayMs / 1000}s"
    is MonitorState.Failed -> detail
}

internal fun shouldShowMonitoringNotification(foregroundActive: Boolean, state: MonitorState): Boolean =
    foregroundActive && state !is MonitorState.Idle
