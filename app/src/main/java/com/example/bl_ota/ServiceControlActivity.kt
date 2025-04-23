package com.example.bl_ota

import com.example.bl_ota.ExpandableServiceListAdapter
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.bl_ota.ble.ConnectionManager

class ServiceControlActivity : AppCompatActivity() {

    private val rssiHandler = Handler(Looper.getMainLooper())
    private val rssiUpdateInterval: Long = 2000

    private val rssiUpdateRunnable = object : Runnable {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun run() {
            ConnectionManager.bluetoothGatt?.readRemoteRssi()
            rssiHandler.postDelayed(this, rssiUpdateInterval)
        }
    }


    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var deviceInfoTextView: TextView

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_service_control)

        val rssiValueTextView = findViewById<TextView>(R.id.RSSIValueTextview)
        val rssiBarsImage = findViewById<ImageView>(R.id.BarsImageView)

        ConnectionManager.onRssiRead = { rssi ->
            runOnUiThread {
                rssiValueTextView.text = "$rssi"
                rssiBarsImage.setImageResource(getSignalStrengthDrawable(rssi))
            }
        }

        rssiHandler.post(rssiUpdateRunnable)

        val mtuValueTextView = findViewById<TextView>(R.id.MTUValTextview)
        val mtuImageView = findViewById<ImageView>(R.id.MTUImage)

        ConnectionManager.onMtuChanged = { mtu ->
            runOnUiThread {
                mtuValueTextView.text = "$mtu"
                // optionally: change MTU image here if you want visual feedback
            }
        }


        val serviceUUIDHeader = findViewById<TextView>(R.id.ServiceUUIDHeadTextView)
        val devicenametextview : TextView = findViewById<TextView>(R.id.DeviceNameTextView)
        val amountServicesCharacteristic : TextView = findViewById<TextView>(R.id.AmountServicesChararacteristicsTextview)

        val expandableListView = findViewById<ExpandableListView>(R.id.expandable_service_list)
        ViewCompat.setOnApplyWindowInsetsListener(expandableListView) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom)
            WindowInsetsCompat.CONSUMED
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
                val characteristicCount = gatt.services.sumOf { it.characteristics.size }

                serviceUUIDHeader.text = "Service UUID:s ($serviceCount)"
                amountServicesCharacteristic.text = "Advertising $serviceCount services and $characteristicCount characteristics"


                val adapter = ExpandableServiceListAdapter(this, serviceData)
                expandableListView.setAdapter(adapter)



            }
        }


        //  info
        ConnectionManager.onConnectionStateChange = {
            runOnUiThread {
                if (ConnectionManager.connected) {
                    Toast.makeText(this, "Connected to ${ConnectionManager.bluetoothGatt?.device?.name}", Toast.LENGTH_SHORT).show()
                    devicenametextview.text = bluetoothDevice.name?.toString()
                } else {
                    Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
                    devicenametextview.text = bluetoothDevice.name?.toString()
                }
            }
        }

    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        ConnectionManager.onMtuChanged = null
        rssiHandler.removeCallbacks(rssiUpdateRunnable)
        ConnectionManager.onRssiRead = null
        ConnectionManager.bluetoothGatt?.close()
        ConnectionManager.bluetoothGatt = null
        ConnectionManager.onServicesDiscovered = null
        ConnectionManager.onConnectionStateChange = null
    }

}
private fun getSignalStrengthDrawable(rssi: Int): Int {
    return when {
        rssi > -60 -> R.drawable.four_bars
        rssi > -70 -> R.drawable.three_bars
        rssi > -80 -> R.drawable.two_bars
        rssi > -90 -> R.drawable.one_bars
        else -> R.drawable.no_bars
    }
}
