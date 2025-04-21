package com.example.bl_ota

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bl_ota.ble.ConnectionManager

class ScanActivity : AppCompatActivity() {
    private lateinit var bluetoothScanner: BluetoothScanner
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var deviceRecycler: RecyclerView
    private lateinit var deviceList: ArrayList<DeviceData>

    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread {
                findViewById<Switch>(R.id.bleSwitch)?.text = if (value) "Stop Scan" else "Start Scan"
            }
        }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }



        bluetoothScanner = BluetoothScanner(this) { result ->
            val device = result.device
            if (deviceList.any { it.address == device.address }) return@BluetoothScanner
            val newDevice = DeviceData(
                deviceType = if (device.name?.lowercase()?.contains("ota") == true) DeviceTypes.KNOWN else DeviceTypes.UNKNOWN,
                rssi = result.rssi,
                name = device.name ?: "Unnamed",
                address = device.address ?: "Unknown",
                lastSeen = System.currentTimeMillis()
            )
            deviceList.add(newDevice)
            deviceRecycler.adapter?.notifyItemInserted(deviceList.lastIndex)
        }

        deviceRecycler = findViewById(R.id.BLEDeviceRecyclerView)
        deviceList = ArrayList()
        deviceRecycler.layoutManager = LinearLayoutManager(this)
        deviceRecycler.adapter = DeviceAdapter(deviceList) { selectedDevice ->
            stopBleScan()

            val intent = Intent(this, DeviceControlActivity::class.java)
            intent.putExtra("device_address", selectedDevice.address)
            startActivity(intent)
//            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
//            val bluetoothAdapter = bluetoothManager.adapter
//            val device = bluetoothAdapter.getRemoteDevice(selectedDevice.address)
//            ConnectionManager.connectToDevice(this, device)
            //Log.d("BLE_OTA","Connecting")
        }

        val selectedBinaryText = findViewById<TextView>(R.id.selectedBinaryText)
        val RSSITextView = findViewById<TextView>(R.id.RSSITextView)
        val deviceTypeTextView = findViewById<TextView>(R.id.DeviceTypeTextView)
        val deviceNameTextView = findViewById<TextView>(R.id.DeviceNameTextView)
        val deviceAddressTextView = findViewById<TextView>(R.id.DeviceAddressTextView)
        val lastSeenTextView = findViewById<TextView>(R.id.LastSeenTextView_yymmdd)
        val bleSwitch = findViewById<Switch>(R.id.bleSwitch)

        val sortHandler: (TextView) -> Unit = { view ->
            val sortBy = view.text.toString()
            Toast.makeText(this, "Sorting by $sortBy", Toast.LENGTH_SHORT).show()
            // TODO: GÖR att det sorteras, kanske enum
        }
        listOf(RSSITextView, deviceTypeTextView, deviceNameTextView, deviceAddressTextView, lastSeenTextView)
            .forEach { it.setOnClickListener { _ -> sortHandler(it) } }

        bleSwitch.setOnClickListener {
            if (isScanning) stopBleScan() else startBleScan()
        }

        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    val fileName = FilePickerHelper.getFileNameFromUri(this, uri)
                    selectedBinaryText.text = fileName?.let { getString(R.string.selectedBinaryName, it) }
                        ?: "Unknown file selected"
                }
            }
        }

        selectedBinaryText.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(intent)
        }
    }




    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startBleScan() {
        if (!hasRequiredBluetoothPermissions()) {
            requestRelevantRuntimePermissions()
        } else {
            bluetoothScanner.startScan()
            isScanning = true
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopBleScan() {
        bluetoothScanner.stopScan()
        isScanning = false
    }

    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasRequiredBluetoothPermissions()) return
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> requestLocationPermission()
            else -> requestBluetoothPermissions()
        }
    }

    private fun requestLocationPermission() = runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle("Location permission required")
            .setMessage("Starting from Android M (6.0), location access is required for BLE scanning.")
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothPermissions() = runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth permission required")
            .setMessage("Starting from Android 12, Bluetooth access permissions are required.")
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
            .show()
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST_CODE) return

        val containsDenial = grantResults.any { it == android.content.pm.PackageManager.PERMISSION_DENIED }
        val allGranted = grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }

        if (containsDenial) {
            requestRelevantRuntimePermissions()
        } else if (allGranted && hasRequiredBluetoothPermissions()) {
            startBleScan()
        } else {
            recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothScanner.isBluetoothEnabled()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                bluetoothEnablingResult.launch(bluetoothScanner.enableBluetoothIntent())
            }
        }
    }

    private val bluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            promptEnableBluetooth()
        }
    }

    private fun promptEnableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        if (!bluetoothScanner.isBluetoothEnabled()) {
            bluetoothEnablingResult.launch(bluetoothScanner.enableBluetoothIntent())
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }


}