package com.warwar13.gentai.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.warwar13.gentai.protocol.FrameStream
import com.warwar13.gentai.protocol.GentaiBle
import com.warwar13.gentai.protocol.ReadCommand
import com.warwar13.gentai.protocol.ReadOnlyGentaiProtocol
import com.warwar13.gentai.protocol.RunningStatus
import com.warwar13.gentai.protocol.decodeBatteryInfo
import com.warwar13.gentai.protocol.decodeCellVoltages
import com.warwar13.gentai.protocol.decodeRunningStatus
import com.warwar13.gentai.protocol.BatteryInfo
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Owns a single GATT connection. All BMS writes call [ReadOnlyGentaiProtocol]. */
@SuppressLint("MissingPermission")
class GentaiBleMonitor(
    private val context: Context,
    private val address: String,
    private val state: MutableStateFlow<MonitorState>,
    private val recording: Boolean = true,
    private val onBatteryInfo: (BatteryInfo, List<Double>, RunningStatus?, Boolean) -> Unit = { _, _, _, _ -> },
    private val onConnectionLost: () -> Unit = {},
    private val onPollingActivity: (Boolean) -> Unit = {},
) {
    @Volatile private var closed = false
    private var gatt: BluetoothGatt? = null
    private var queryCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var notificationsReady = false
    @Volatile private var connectionError: String? = null
    @Volatile private var lastValidTelemetryElapsedMs = 0L
    private val writeMutex = Mutex()
    private val stream = FrameStream { error -> Log.w(TAG, "Rejected notification: ${error.message}") }
    private var pollNumber = 0L
    private var failures = 0
    @Volatile private var pendingCommand: Int? = null
    @Volatile private var writeCompleted: CompletableDeferred<Unit>? = null
    @Volatile private var responseReceived: CompletableDeferred<Unit>? = null
    private var latestCellsV: List<Double> = emptyList()
    private var latestStatus: RunningStatus? = null
    private var latestBatteryInfo: BatteryInfo? = null
    private var firstTelemetryInConnection = true

    suspend fun run() {
        while (!closed) {
            state.value = MonitorState.Connecting
            val attemptStartedAt = SystemClock.elapsedRealtime()
            try {
                onPollingActivity(true)
                resetConnectionState()
                connect()
                while (!closed && gatt != null) {
                    onPollingActivity(true)
                    val commands = MonitorPolicy.commandsForPoll(pollNumber++)
                    query(commands.first())
                    commands.drop(1).forEach { command ->
                        try {
                            query(command, attempts = 1, responseTimeoutMs = OPTIONAL_RESPONSE_TIMEOUT_MS)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            when (command) {
                                ReadCommand.RUNNING_STATUS -> latestStatus = null
                                ReadCommand.CELL_VOLTAGES -> latestCellsV = emptyList()
                                else -> Unit
                            }
                            Log.w(TAG, "Optional ${command.name} query unavailable: ${error.message}")
                        }
                    }
                    latestBatteryInfo?.let { info ->
                        lastValidTelemetryElapsedMs = SystemClock.elapsedRealtime()
                        state.value = MonitorState.Connected(
                            voltageV = info.voltageV,
                            currentA = info.currentA,
                            socPct = info.socPct,
                            livePowerW = livePowerWatts(info.voltageV, info.currentA),
                            estimate = estimateBatteryTime(info.socPct, info.remainingAh, info.fullAh, info.currentA),
                            recording = recording,
                        )
                        onBatteryInfo(info, latestCellsV, latestStatus, firstTelemetryInConnection)
                        firstTelemetryInConnection = false
                    }
                    delay(MonitorPolicy.POLL_INTERVAL_MS)
                }
                if (!closed) error("Bluetooth connection lost")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is SecurityException) throw error
                if (closed) return
                onConnectionLost()
                Log.w(TAG, "Monitoring connection will recover: ${error.message}")
                if (lastValidTelemetryElapsedMs - attemptStartedAt >= STABLE_CONNECTION_MS) failures = 0
                when (val recovery = recoveryFor(error.message, failures)) {
                    RecoveryAction.WaitForBluetooth -> {
                        state.value = MonitorState.BluetoothUnavailable
                        onPollingActivity(false)
                        awaitBluetoothEnabled()
                        continue
                    }
                    is RecoveryAction.Reconnect -> {
                        onPollingActivity(true)
                        failures++
                        state.value = MonitorState.Reconnecting(recovery.delayMs, error.message ?: "Bluetooth connection lost")
                        delay(recovery.delayMs)
                    }
                }
            } finally {
                val activeGatt = gatt
                gatt = null
                activeGatt?.let(::safeClose)
                resetConnectionState()
            }
        }
    }

    /** No polling timer or CPU lock while Bluetooth is off. Registration precedes the
     * initial check so an enable event cannot be missed between checking and waiting. */
    private suspend fun awaitBluetoothEnabled() {
        callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON
                    ) {
                        if (!closed) onPollingActivity(true)
                        trySend(true)
                    }
                }
            }
            // Bluetooth broadcasts can originate from a privileged non-system UID.
            ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            try {
                if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == true) {
                    if (!closed) onPollingActivity(true)
                    trySend(true)
                }
            } catch (error: Exception) {
                context.unregisterReceiver(receiver)
                throw error
            }
            awaitClose { context.unregisterReceiver(receiver) }
        }.first { it }
    }

    fun close() {
        closed = true
        responseReceived?.cancel()
        writeCompleted?.cancel()
        val activeGatt = gatt
        gatt = null
        activeGatt?.let(::safeDisconnectAndClose)
        resetConnectionState()
    }

    private fun resetConnectionState() {
        firstTelemetryInConnection = true
        queryCharacteristic = null
        notificationsReady = false
        connectionError = null
        stream.reset()
        latestCellsV = emptyList()
        latestStatus = null
        latestBatteryInfo = null
        pollNumber = 0
        pendingCommand = null
        writeCompleted = null
        responseReceived = null
    }

    private suspend fun connect() {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: error("Bluetooth is unavailable")
        check(adapter.isEnabled) { "Bluetooth is disabled" }
        val device = adapter.getRemoteDevice(address)
        gatt = bluetoothCall { device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }
        var elapsed = 0L
        while (!closed && !notificationsReady && connectionError == null && elapsed < 15_000L) { delay(100); elapsed += 100 }
        connectionError?.let { error(it) }
        check(queryCharacteristic != null && notificationsReady) { "Timed out enabling BMS notifications" }
        query(ReadCommand.HANDSHAKE)
        // Identity metadata is optional in the reference implementation, is not
        // displayed by this app, and is not supported by every compatible pack.
        // Start telemetry immediately instead of delaying it with optional reads.
    }

    private suspend fun query(command: ReadCommand, attempts: Int = 2, responseTimeoutMs: Long = MonitorPolicy.RESPONSE_TIMEOUT_MS) = writeMutex.withLock {
        require(attempts > 0)
        val characteristic = checkNotNull(queryCharacteristic) { "GATT query characteristic unavailable" }
        val bytes = ReadOnlyGentaiProtocol.buildReadQuery(command.value)
        repeat(attempts) { attempt ->
            val written = CompletableDeferred<Unit>()
            val response = CompletableDeferred<Unit>()
            pendingCommand = command.value
            writeCompleted = written
            responseReceived = response
            val activeGatt = checkNotNull(gatt) { "GATT connection unavailable" }
            val accepted = bluetoothCall {
                if (Build.VERSION.SDK_INT >= 33) activeGatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                else { characteristic.value = bytes; activeGatt.writeCharacteristic(characteristic) }
            }
            if (accepted && awaitCompletion(written, WRITE_COMPLETION_TIMEOUT_MS) && awaitCompletion(response, responseTimeoutMs)) {
                pendingCommand = null
                writeCompleted = null
                responseReceived = null
                return@withLock
            }
            pendingCommand = null
            writeCompleted = null
            responseReceived = null
            if (attempt == attempts - 1) error("No ${command.name} response${if (attempts > 1) " after retry" else ""}")
        }
    }

    private suspend fun awaitCompletion(signal: CompletableDeferred<Unit>, timeoutMs: Long): Boolean = try {
        withTimeout(timeoutMs) { signal.await() }
        true
    } catch (_: TimeoutCancellationException) {
        false
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== this@GentaiBleMonitor.gatt) { safeClose(gatt); return }
            if (status != BluetoothGatt.GATT_SUCCESS || newState != BluetoothProfile.STATE_CONNECTED) {
                failConnection(gatt, "GATT disconnected (status $status)")
                return
            }
            Log.i(TAG, "GATT connected; discovering services")
            val accepted = runCatching { bluetoothCall { gatt.discoverServices() } }.getOrElse {
                failConnection(gatt, "Bluetooth permission unavailable")
                return
            }
            if (!accepted) failConnection(gatt, "Could not discover BMS GATT services")
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== this@GentaiBleMonitor.gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) return failConnection(gatt, "GATT service discovery failed (status $status)")
            val service = gatt.getService(UUID.fromString(GentaiBle.serviceUuid))
                ?: return failConnection(gatt, "Compatible BMS GATT service unavailable")
            queryCharacteristic = service.getCharacteristic(UUID.fromString(GentaiBle.writeCharacteristicUuid))
                ?: return failConnection(gatt, "BMS query characteristic unavailable")
            val notify = service.getCharacteristic(UUID.fromString(GentaiBle.notifyCharacteristicUuid))
                ?: return failConnection(gatt, "BMS notification characteristic unavailable")
            val locallyEnabled = runCatching { bluetoothCall { gatt.setCharacteristicNotification(notify, true) } }.getOrElse {
                return failConnection(gatt, "Bluetooth permission unavailable")
            }
            if (!locallyEnabled) return failConnection(gatt, "Could not enable local GATT notifications")
            val descriptor = notify.getDescriptor(UUID.fromString(CCCD_UUID))
                ?: return failConnection(gatt, "GATT notification descriptor unavailable")
            val accepted = runCatching { bluetoothCall {
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            } }.getOrElse { return failConnection(gatt, "Bluetooth permission unavailable") }
            if (!accepted) failConnection(gatt, "GATT notification descriptor write was rejected")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== this@GentaiBleMonitor.gatt || !descriptor.uuid.toString().equals(CCCD_UUID, ignoreCase = true)) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notificationsReady = true
                Log.i(TAG, "GATT notifications enabled")
            }
            else failConnection(gatt, "GATT notification descriptor failed (status $status)")
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (gatt !== this@GentaiBleMonitor.gatt || characteristic.uuid.toString() != GentaiBle.writeCharacteristicUuid) return
            if (status == BluetoothGatt.GATT_SUCCESS) writeCompleted?.complete(Unit)
            else writeCompleted?.completeExceptionally(IllegalStateException("GATT query write failed (status $status)"))
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (gatt !== this@GentaiBleMonitor.gatt) return
            handleNotification(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (gatt !== this@GentaiBleMonitor.gatt) return
            handleNotification(value)
        }

        private fun handleNotification(value: ByteArray) {
            stream.push(value).forEach { frame ->
                val valid = when (frame.command) {
                    ReadCommand.CELL_VOLTAGES.value -> runCatching { decodeCellVoltages(frame.payload) }.onSuccess { latestCellsV = it }.isSuccess
                    ReadCommand.RUNNING_STATUS.value -> runCatching { decodeRunningStatus(frame.payload) }.onSuccess { latestStatus = it }.isSuccess
                    ReadCommand.BATTERY_INFO.value -> runCatching { decodeBatteryInfo(frame.payload) }.onSuccess { info ->
                        latestBatteryInfo = info
                    }.isSuccess
                    else -> true
                }
                if (valid && frame.command == pendingCommand) responseReceived?.complete(Unit)
            }
        }

        private fun failConnection(gatt: BluetoothGatt, message: String) {
            if (gatt !== this@GentaiBleMonitor.gatt) return
            connectionError = message
            responseReceived?.completeExceptionally(IllegalStateException(message))
            writeCompleted?.completeExceptionally(IllegalStateException(message))
            this@GentaiBleMonitor.gatt = null
            safeDisconnectAndClose(gatt)
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private inline fun <T> bluetoothCall(block: () -> T): T {
        check(hasBluetoothConnectPermission()) { "Bluetooth permission unavailable" }
        return block()
    }

    private fun safeClose(gatt: BluetoothGatt) {
        runCatching { bluetoothCall { gatt.close() } }
    }

    private fun safeDisconnectAndClose(gatt: BluetoothGatt) {
        runCatching { bluetoothCall { gatt.disconnect() } }
        safeClose(gatt)
    }
    private companion object {
        const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        const val STABLE_CONNECTION_MS = 30_000L
        const val WRITE_COMPLETION_TIMEOUT_MS = 3_000L
        const val OPTIONAL_RESPONSE_TIMEOUT_MS = 1_500L
        const val TAG = "GentaiBleMonitor"
    }
}
