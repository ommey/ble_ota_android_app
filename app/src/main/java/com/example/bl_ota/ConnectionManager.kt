@file:Suppress("DEPRECATION")

package com.example.bl_ota

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import java.util.Date
import java.util.Locale
import java.util.UUID
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
    var onCharacteristicWrite: ((BluetoothGattCharacteristic, Boolean) -> Unit)? = null
    val pendingWriteMap: MutableMap<UUID, String> = mutableMapOf()
    val pendingViewMap = mutableMapOf<UUID, Pair<TextView, ImageView>>()
    var onCharacteristicRead: ((BluetoothGattCharacteristic, ByteArray?, Boolean) -> Unit)? = null
    val pendingReadMap = mutableMapOf<UUID, Pair<TextView, ImageView>>()
    val notificationViewMap = mutableMapOf<UUID, Triple<TextView, ImageView, () -> Int>>() // includes counter function
    val indicationViewMap = mutableMapOf<UUID, Triple<TextView, ImageView, () -> Long>>()
    var negotiatedMtu: Int = 23
    var startOtaProcedure: (() -> Unit)? = null
    var onOtaConfirmed: (() -> Unit)? = null

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

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            onCharacteristicRead?.invoke(characteristic, characteristic.value, success)
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            onCharacteristicWrite?.invoke(characteristic, success)
        }
        
        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onRssiRead?.invoke(rssi)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            Log.i("GattCallback", "Descriptor write completed for ${descriptor.uuid}, success=$success")

            if (descriptor.uuid == UUID.fromString(CCC_DESCRIPTOR_UUID) && success) {
                val charUUID = descriptor.characteristic.uuid
                if (charUUID == stm_ota_file_upload_reboot_confirmation_characteristic_uuid) {
                    Handler(Looper.getMainLooper()).post {
                        startOtaProcedure?.invoke()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            negotiatedMtu = mtu
            Log.i("GattCallback", "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
            Handler(Looper.getMainLooper()).post {
                onMtuChanged?.invoke(mtu)
                gatt.discoverServices()
            }
        }

        @SuppressLint("SetTextI18n")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == stm_ota_file_upload_reboot_confirmation_characteristic_uuid) {
                val value = characteristic.value
                if (value.isNotEmpty() && value[0] == 0x01.toByte()) {
                    Log.i("OTA", "OTA confirmation received: ${value.joinToString(" ") { "0x%02X".format(it) }}")

                    Handler(Looper.getMainLooper()).post {
                        onOtaConfirmed?.invoke()
                    }
                }
            }
            
            val uuid = characteristic.uuid.toString().lowercase()

             if (uuid == stm_ota_file_upload_reboot_confirmation_characteristic_uuid.toString().lowercase()) {
                 Handler(Looper.getMainLooper()).post {
                     Log.i("OTA", "OTA confirmation characteristic changed (value: ${characteristic.value?.joinToString(" ") { "0x%02X".format(it) }})")
                 }
                 return
             }

             val viewSet = notificationViewMap[characteristic.uuid] ?: indicationViewMap[characteristic.uuid] ?: return
             val (statusText, icon, getDiff) = viewSet

             val sinceLast = getDiff()
             val nowStr = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

             Handler(Looper.getMainLooper()).post {
                 icon.setImageResource(R.drawable.response)
                 statusText.text = "Last at: $nowStr\nΔ ${sinceLast}ms"
             }

             Handler(Looper.getMainLooper()).postDelayed({
                 icon.setImageResource(R.drawable.success)
             }, 200)
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

    @SuppressLint("MissingPermission")
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.readCharacteristic(characteristic)
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.writeCharacteristic(characteristic)
    }

    @SuppressLint("MissingPermission")
    fun toggleNotifications(characteristic: BluetoothGattCharacteristic, enable: Boolean) {
        val gatt = bluetoothGatt ?: return
        gatt.setCharacteristicNotification(characteristic, enable)

        val descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID)) ?: return
        descriptor.value = if (enable)
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        else
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

        gatt.writeDescriptor(descriptor)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingPermission")
    fun toggleIndications(characteristic: BluetoothGattCharacteristic, enable: Boolean) {
        if (enable) {
            enableIndications(characteristic)
        } else {
            disableIndications(characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    fun disableIndications(characteristic: BluetoothGattCharacteristic) {
        val gatt = bluetoothGatt ?: return

        gatt.setCharacteristicNotification(characteristic, false)

        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val descriptor = characteristic.getDescriptor(cccdUuid) ?: return

        descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)

        Log.d("BLE", "Indications disabled for ${characteristic.uuid}")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @JvmStatic
    @SuppressLint("MissingPermission")
    fun enableIndications(characteristic: BluetoothGattCharacteristic) {
        val gatt = bluetoothGatt ?: return
        gatt.setCharacteristicNotification(characteristic, true) 

        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val descriptor = characteristic.getDescriptor(cccdUuid)
        descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        gatt.writeDescriptor(descriptor)

        val value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE

        if (descriptor != null) {
            if (bluetoothGatt?.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS) {
                Log.d("DESCRIPTOR", "indications for $characteristic")
                return
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun resetConnectionHandler(){
        bluetoothGatt?.apply {
            close()
        }
        bluetoothGatt = null
        onConnectionStateChange = null
        onServicesDiscovered = null
        onRssiRead = null
        onCharacteristicWrite = null
        onCharacteristicRead = null
        onMtuChanged = null
        startOtaProcedure = null
        pendingWriteMap.clear()
        pendingReadMap.clear()
        pendingViewMap.clear()
        notificationViewMap.clear()
        indicationViewMap.clear()
        onOtaConfirmed = null
    }
    
    @SuppressLint("PrivateApi")
    fun BluetoothGatt.refresh(): Boolean {
        return try {
            val refreshMethod = BluetoothGatt::class.java.getMethod("refresh")
            refreshMethod.isAccessible = true
            refreshMethod.invoke(this) as Boolean
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

