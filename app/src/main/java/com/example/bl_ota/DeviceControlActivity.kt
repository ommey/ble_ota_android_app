package com.example.bl_ota

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import com.example.bl_ota.ble.ConnectionManager

class DeviceControlActivity : AppCompatActivity() {

    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var deviceInfoTextView: TextView

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_control)

        deviceInfoTextView = findViewById(R.id.deviceInfoTextView)

        val deviceAddress = intent.getStringExtra("device_address")
        if (deviceAddress == null) {
            Toast.makeText(this, "Device address is missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)

        deviceInfoTextView.text = "Connecting to $deviceAddress..."

        ConnectionManager.onServicesDiscovered = { gatt ->
            runOnUiThread {
                deviceInfoTextView.text = "Connected! Services:\n"
                gatt.services.forEach { service ->
                    deviceInfoTextView.append("\nService: ${service.uuid}")
                    service.characteristics.forEach {
                        deviceInfoTextView.append("\n  - Char: ${it.uuid}")
                    }
                }
            }
        }

        ConnectionManager.onConnectionStateChange = {
            runOnUiThread {
                if (ConnectionManager.connected)
                {
                    Toast.makeText(this, "Connected to ${ConnectionManager.bluetoothGatt?.device?.name}", Toast.LENGTH_SHORT).show()

                } else {
                    Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
                }

            }
        }

        ConnectionManager.connectToDevice(this, bluetoothDevice)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        if (ConnectionManager.bluetoothGatt != null){
            Toast.makeText(this, "Disconnected from ${ConnectionManager.bluetoothGatt?.device?.name}", Toast.LENGTH_SHORT).show()
            ConnectionManager.bluetoothGatt?.close()
            ConnectionManager.bluetoothGatt = null
        } else {
            Toast.makeText(this, "Connection attempt to ${ConnectionManager.bluetoothGatt?.device?.name} aborted", Toast.LENGTH_SHORT).show()
            ConnectionManager.bluetoothGatt = null

        }
        ConnectionManager.onServicesDiscovered = null
        ConnectionManager.onConnectionStateChange = null
    }
}
