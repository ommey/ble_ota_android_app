package com.example.bl_ota

import com.example.bl_ota.ExpandableServiceListAdapter
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Bundle
import android.widget.ExpandableListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.bl_ota.ble.ConnectionManager

class ServiceControlActivity : AppCompatActivity() {

    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var deviceInfoTextView: TextView

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_service_control)

        val serviceUUIDHeader = findViewById<TextView>(R.id.ServiceUUIDHeadTextView)

        val expandableListView = findViewById<ExpandableListView>(R.id.expandable_service_list)
        ViewCompat.setOnApplyWindowInsetsListener(expandableListView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val serviceData: MutableMap<BluetoothGattService, List<BluetoothGattCharacteristic>> = LinkedHashMap()

        // Hämta device address
        val deviceAddress = intent.getStringExtra("device_address")
        if (deviceAddress == null) {
            Toast.makeText(this, "Device address is missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)

        // Initiera anslutning
        ConnectionManager.connectToDevice(this, bluetoothDevice)

        // Lyssna på services
        ConnectionManager.onServicesDiscovered = { gatt ->
            runOnUiThread {
                serviceData.clear()
                gatt.services.forEach { service ->
                    serviceData[service] = service.characteristics
                }

                // Get a readable list of service UUIDs
                val serviceUUIDs = gatt.services.joinToString("\n") { it.uuid.toString() }

//                // Now update your header text
//                serviceUUIDHeader.text = "Available Services:\n${serviceUUIDs.count()}"

                val serviceCount = gatt.services.size
                serviceUUIDHeader.text = "Service UUID:s ($serviceCount)"

                val adapter = ExpandableServiceListAdapter(this, serviceData)
                expandableListView.setAdapter(adapter)



            }
        }

        //  info
        ConnectionManager.onConnectionStateChange = {
            runOnUiThread {
                if (ConnectionManager.connected) {
                    Toast.makeText(this, "Connected to ${ConnectionManager.bluetoothGatt?.device?.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        ConnectionManager.bluetoothGatt?.close()
        ConnectionManager.bluetoothGatt = null
        ConnectionManager.onServicesDiscovered = null
        ConnectionManager.onConnectionStateChange = null
    }
}
