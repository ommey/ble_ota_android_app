package com.example.bl_ota

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ServiceControlActivity : AppCompatActivity() {

    private val rssiHandler = Handler(Looper.getMainLooper())
    private val rssiUpdateInterval: Long = 2000
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private var onFilePickedCallback: ((Uri) -> Unit)? = null

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
        Log.w("LIFECYCLE", "ServiceControlActivity has been created!!!")
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

        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    onFilePickedCallback?.invoke(uri)
                }
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

                val serviceCount = gatt.services.size
                val characteristicCount = gatt.services.sumOf { it.characteristics.size }

                amountServicesCharacteristic.text = "Advertising $serviceCount services and $characteristicCount characteristics"


                val adapter = ExpandableServiceListAdapter(this, serviceData)
                expandableListView.setAdapter(adapter)

                val matchedFeatures = featureCatalog.filter { feature ->
                    val services = gatt.services
                    val allCharacteristics = services.flatMap { it.characteristics }

                    val hasAllServices = feature.serviceUUIDs.all { uuid -> services.any { it.uuid == uuid } }
                    val hasAllCharacteristics = feature.characteristicUUIDs.all { uuid ->
                        allCharacteristics.any { it.uuid == uuid }
                    }

                    hasAllServices && hasAllCharacteristics
                }


                expandableListView.setOnGroupExpandListener { groupPosition ->
                    adapter.expandingGroup = groupPosition
                    adapter.notifyDataSetChanged()
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

    fun launchFilePicker(onPicked: (Uri) -> Unit) {
        onFilePickedCallback = onPicked
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        Log.w("LIFECYCLE", "💀 ServiceControlActivity has been destroyed!")
        MqttManager.clearRetrieveFirmwareCallback()
        rssiHandler.removeCallbacks(rssiUpdateRunnable)
        ConnectionManager.resetConnectionHandler()
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
