// ConnectionManager.kt
package com.example.bl_ota.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
    var bluetoothGatt: BluetoothGatt? = null
    var onServicesDiscovered: ((BluetoothGatt) -> Unit)? = null
    var onConnectionStateChange: ((BluetoothGatt) -> Unit)? = null
    var onRssiRead: ((Int) -> Unit)? = null
    var connected: Boolean = false
    var onMtuChanged: ((Int) -> Unit)? = null


    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            val deviceName = gatt.device.name
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i("GattCallback", "Connected to $deviceAddress")
                        bluetoothGatt = gatt
                        Handler(Looper.getMainLooper()).post {
                            gatt.requestMtu(GATT_MAX_MTU_SIZE)
                        }
                        connected = true
                        onConnectionStateChange?.invoke(gatt)

                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i("GattCallback", "Disconnected from $deviceAddress")
                        onConnectionStateChange?.invoke(gatt)
                        connected = false
                        gatt.close()


                    }
                }
            } else {
                Log.e("GattCallback", "Error $status on $deviceAddress, disconnecting")
                connected = false
                gatt.close()
            }
            signalEndOfOperation()
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onRssiRead?.invoke(rssi)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i("GattCallback", "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
            Handler(Looper.getMainLooper()).post {
                onMtuChanged?.invoke(mtu)
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onServicesDiscovered?.invoke(gatt)
                Log.i("GattCallback", "Service discovery complete")
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

    fun toggleNotifications(characteristic: BluetoothGattCharacteristic) {

    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {

    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.writeCharacteristic(characteristic)
    }
    fun toggleIndications(characteristic: BluetoothGattCharacteristic) {

    }


}

