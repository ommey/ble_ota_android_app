// ConnectionManager.kt
package com.example.bl_ota.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

private const val GATT_MAX_MTU_SIZE = 517
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"

sealed class BleOperationType {
    abstract val device: BluetoothDevice
    data class Connect(override val device: BluetoothDevice, val context: Context) : BleOperationType()
}

object ConnectionManager {
    private val operationQueue = ConcurrentLinkedQueue<BleOperationType>()
    private var pendingOperation: BleOperationType? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i("GattCallback", "Connected to $deviceAddress")
                        bluetoothGatt = gatt
                        Handler(Looper.getMainLooper()).post {
                            gatt.requestMtu(GATT_MAX_MTU_SIZE)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i("GattCallback", "Disconnected from $deviceAddress")
                        gatt.close()
                    }
                }
            } else {
                Log.e("GattCallback", "Error $status on $deviceAddress, disconnecting")
                gatt.close()
            }
            signalEndOfOperation()
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i("GattCallback", "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
            Handler(Looper.getMainLooper()).post {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.printGattTable()
                Log.i("GattCallback", "Service discovery complete")
            } else {
                Log.e("GattCallback", "Service discovery failed with status $status")
            }
        }
    }

    @Synchronized
    fun connectToDevice(context: Context, device: BluetoothDevice) {
        enqueueOperation(BleOperationType.Connect(device, context))
    }

    @Synchronized
    private fun enqueueOperation(operation: BleOperationType) {
        operationQueue.add(operation)
        if (pendingOperation == null) doNextOperation()
    }

    @Synchronized
    private fun doNextOperation() {
        if (pendingOperation != null) return

        val operation = operationQueue.poll() ?: return
        pendingOperation = operation

        when (operation) {
            is BleOperationType.Connect -> connect(operation.context, operation.device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(context: Context, device: BluetoothDevice) {
        Log.i("ConnectionManager", "Connecting to ${device.address}")
        device.connectGatt(context, false, gattCallback)
    }

    @Synchronized
    private fun signalEndOfOperation() {
        pendingOperation = null
        if (operationQueue.isNotEmpty()) doNextOperation()
    }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i("printGattTable", "No service/characteristic available")
            return
        }
        services.forEach { service ->
            val chars = service.characteristics.joinToString("\n|--") { it.uuid.toString() }
            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n|--$chars")
        }
    }
}
