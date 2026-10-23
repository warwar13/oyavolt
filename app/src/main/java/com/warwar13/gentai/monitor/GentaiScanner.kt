package com.warwar13.gentai.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.warwar13.gentai.protocol.GentaiBle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DiscoveredBattery(val name: String, val address: String, val rssi: Int)

class GentaiScanner(private val context: Context) {
    @Volatile private var scanning = false
    private val _devices = MutableStateFlow<List<DiscoveredBattery>>(emptyList())
    val devices: StateFlow<List<DiscoveredBattery>> = _devices
    private val callback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasPermissions()) return
            val name = result.device.name ?: return
            if (GentaiBle.namePrefixes.none(name::startsWith)) return
            val device = DiscoveredBattery(name, result.device.address, result.rssi)
            _devices.value = (_devices.value.filterNot { it.address == device.address } + device).sortedBy { it.name }
        }
        override fun onScanFailed(errorCode: Int) { scanning = false }
    }
    @SuppressLint("MissingPermission") fun start() {
        if (scanning || !hasPermissions()) return
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.let { scanner ->
            scanning = true
            scanner.startScan(callback)
        }
    }
    @SuppressLint("MissingPermission") fun stop() {
        if (!scanning) return
        scanning = false
        if (!hasPermissions()) return
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(callback)
    }

    private fun hasPermissions(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
