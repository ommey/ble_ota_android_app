package com.example.bl_ota

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission



class BluetoothScanner(
    private val context: Context,
    private val onDeviceFound: (ScanResult) -> Unit
) {

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }


    private val bleScanner by lazy { bluetoothAdapter.bluetoothLeScanner }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            onDeviceFound(result)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        bleScanner.startScan(null, scanSettings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        bleScanner.stopScan(scanCallback)
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter.isEnabled

    fun enableBluetoothIntent() = android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
}
