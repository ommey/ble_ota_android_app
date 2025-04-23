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
import android.view.View
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.bl_ota.ble.ConnectionManager
import com.example.bl_ota.ui.adapters.FeatureExpandableAdapter
import com.example.bl_ota.ui.adapters.FeatureListAdapter
import com.example.bl_ota.util.featureCatalog

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


        val devicenametextview : TextView = findViewById<TextView>(R.id.DeviceNameTextView)
        val amountServicesCharacteristic : TextView = findViewById<TextView>(R.id.AmountServicesChararacteristicsTextview)

        val expandableListView = findViewById<ExpandableListView>(R.id.expandable_service_list)
        ViewCompat.setOnApplyWindowInsetsListener(expandableListView) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ConnectionManager.onCharacteristicRead = { characteristic, value, success ->
            runOnUiThread {
                val (statusText, icon) = ConnectionManager.pendingReadMap.remove(characteristic.uuid) ?: return@runOnUiThread

                icon.animate().cancel()
                icon.rotation = 0f

                if (success && value != null) {
                    icon.setImageResource(R.drawable.success)

                    val hex = value.joinToString(" ") { "0x%02X".format(it) }
                    val text = try {
                        val decoded = value.toString(Charsets.UTF_8)
                        if (decoded.all { it.isISOControl() }) null else decoded // avoid showing junk
                    } catch (e: Exception) {
                        null
                    }

                    statusText.text = buildString {
                        append("Hex: $hex")
                        if (!text.isNullOrBlank()) append("\nText: $text")
                    }

                } else {
                    icon.setImageResource(R.drawable.fail)
                    statusText.text = "Read failed"
                }
            }
        }


        ConnectionManager.onCharacteristicWrite = { characteristic, success ->
            runOnUiThread {
                val uuid = characteristic.uuid
                val lastSent = ConnectionManager.pendingWriteMap.remove(uuid) ?: "(unknown)"
                val (statusText, icon) = ConnectionManager.pendingViewMap.remove(uuid) ?: return@runOnUiThread

                icon.animate().cancel()
                icon.rotation = 0f

                if (success) {
                    icon.setImageResource(R.drawable.response)
                    statusText.text = "Sent: $lastSent"
                } else {
                    icon.setImageResource(R.drawable.no_response)
                    statusText.text = "Not confirmed : $lastSent"
                }
            }
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

                amountServicesCharacteristic.text = "Advertising $serviceCount services and $characteristicCount characteristics"


                val adapter = ExpandableServiceListAdapter(this, serviceData)
                expandableListView.setAdapter(adapter)

                val matchedFeatures = featureCatalog.filter { feature ->
                    feature.serviceUUIDs.all { uuid -> gatt.services.any { it.uuid == uuid } } &&
                            feature.characteristicUUIDs.all { uuid ->
                                gatt.services.any { service -> service.characteristics.any { it.uuid == uuid } }
                            }
                }

                val featureExpandableList = findViewById<ExpandableListView>(R.id.feature_expandable_list)
                if (matchedFeatures.isNotEmpty()) {
                    val featureAdapter = FeatureExpandableAdapter(this, gatt, matchedFeatures)
                    featureExpandableList.setAdapter(featureAdapter)
                    featureExpandableList.visibility = View.VISIBLE
                } else {
                    featureExpandableList.visibility = View.GONE
                }


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
