package com.example.bl_ota

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
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

class ScanActivity : AppCompatActivity() {
    private lateinit var bluetoothScanner: BluetoothScanner
    private lateinit var deviceRecycler: RecyclerView
    private lateinit var deviceList: ArrayList<DeviceData>
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private var isScanning = false
    private var currentSort: SortOption = SortOption.LAST_SEEN

    enum class SortOption {
        RSSI, DEVICE_TYPE, NAME, ADDRESS, LAST_SEEN
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

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            resetDeviceListAndConnections()
            swipeRefreshLayout.isRefreshing = false
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

            val intent = Intent(this, ServiceControlActivity::class.java)
            intent.putExtra("device_address", selectedDevice.address)
            startActivity(intent)
        }

        val rssiTextView = findViewById<TextView>(R.id.RSSITextView)
        val deviceTypeTextView = findViewById<TextView>(R.id.DeviceTypeTextView)
        val deviceNameTextView = findViewById<TextView>(R.id.DeviceNameTextView)
        val deviceAddressTextView = findViewById<TextView>(R.id.DeviceAddressTextView)
        val lastSeenTextView = findViewById<TextView>(R.id.LastSeenTextView_yymmdd)

        val sortHandler: (TextView) -> Unit = { view ->
            when (view.id) {
                R.id.RSSITextView -> sortDeviceList(SortOption.RSSI)
                R.id.DeviceTypeTextView -> sortDeviceList(SortOption.DEVICE_TYPE)
                R.id.DeviceNameTextView -> sortDeviceList(SortOption.NAME)
                R.id.DeviceAddressTextView -> sortDeviceList(SortOption.ADDRESS)
                R.id.LastSeenTextView_yymmdd -> sortDeviceList(SortOption.LAST_SEEN)
            }
        }

        listOf(rssiTextView, deviceTypeTextView, deviceNameTextView, deviceAddressTextView, lastSeenTextView)
            .forEach { it.setOnClickListener { _ -> sortHandler(it) }
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onResume() {
        super.onResume()

        if (!bluetoothScanner.isBluetoothEnabled()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                bluetoothEnablingResult.launch(bluetoothScanner.enableBluetoothIntent())
            }
        } else {
            resetDeviceListAndConnections() // ← Trigger reset
            startBleScan()                  // ← Restart scan
        }
    }

    @SuppressLint("MissingPermission", "NotifyDataSetChanged")
    fun resetDeviceListAndConnections() {
        deviceList.clear()
        deviceRecycler.adapter?.notifyDataSetChanged()
        ConnectionManager.resetConnectionHandler()
    }

    private val bluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            promptEnableBluetooth()
        }
    }

    private fun promptEnableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        if (!bluetoothScanner.isBluetoothEnabled()) {
            bluetoothEnablingResult.launch(bluetoothScanner.enableBluetoothIntent())
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun sortDeviceList(option: SortOption) {
        currentSort = option
        when (option) {
            SortOption.RSSI -> deviceList.sortByDescending { it.rssi }
            SortOption.DEVICE_TYPE -> deviceList.sortBy { it.deviceType.name }
            SortOption.NAME -> deviceList.sortBy { it.name.lowercase() }
            SortOption.ADDRESS -> deviceList.sortBy { it.address }
            SortOption.LAST_SEEN -> deviceList.sortByDescending { it.lastSeen }
        }
        deviceRecycler.adapter?.notifyDataSetChanged()
    }


    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }
}