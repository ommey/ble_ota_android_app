@file:Suppress("DEPRECATION")

package com.example.bl_ota

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.bl_ota.ConnectionManager.refresh
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.util.UUID
import android.os.Handler
import android.os.Looper
import android.widget.Toast

const val stm_ota_service_uuid: String = "0000FE20-cc7a-482a-984a-7f2ed5b3e58f"
const val stm_ota_base_address_characteristic_uuid: String = "0000FE22-8e22-4541-9d4c-21edae82ed19"
const val stm_ota_ota_raw_data_characteristic_uuid: String = "0000FE24-8e22-4541-9d4c-21edae82ed19"
const val stm_ota_reboot_request_characteristic_uuid: String = "0000FE11-8e22-4541-9d4c-21edae82ed19"
val stm_ota_file_upload_reboot_confirmation_characteristic_uuid: UUID? =
    UUID.fromString("0000FE23-8e22-4541-9d4c-21edae82ed19")


const val ble_fota_service_uuid: String = "b1e0f07a-0000-0000-0000-000baad0b055"
const val ble_fota_base_address_characteristic_uuid: String = "b1e0f07a-0001-0000-0000-000baad0b055"
const val ble_fota_raw_data_characteristic_uuid: String = "b1e0f07a-0003-0000-0000-000baad0b055"
const val ble_fota_reboot_request_characteristic_uuid: String = "b1e0f07a-0004-0000-0000-000baad0b055"

//val my_boot_service_uuid = UUID.fromString("b1e0f07a-0000-0000-0000-00000baad0b055")
//val my_boot_base_char_uuid = UUID.fromString("b1e0f07a-0001-0000-0000-00000baad0b055")
//val my_boot_raw_data_char_uuid = UUID.fromString("b1e0f07a-0003-0000-0000-00000baad0b055")
//val my_boot_reboot_char_uuid = UUID.fromString("b1e0f07a-0004-0000-0000-00000baad0b055")



var otaStartTime: Long = 0
var otaEndTime: Long = 0
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@SuppressLint("MissingPermission", "SetTextI18n")
val featureCatalog = listOf(
    DiscoveredFeature(
        name = "BLE FoTA",
        layoutResId = R.layout.feature_stm_ota,
        serviceUUIDs = listOf(UUID.fromString(ble_fota_service_uuid)),
        characteristicUUIDs = listOf(
            UUID.fromString(ble_fota_base_address_characteristic_uuid),
            UUID.fromString(ble_fota_raw_data_characteristic_uuid)
        ),
        binder = { view, gatt ->
            val statusText = view.findViewById<TextView>(R.id.statusTextView)
            val progressBar = view.findViewById<ProgressBar>(R.id.updateProgressBar)
            val selectFileButton = view.findViewById<Button>(R.id.selectFileButton)
            val selectCloudFileButton = view.findViewById<Button>(R.id.selectCloudFileButton)
            val selectedFileTextView = view.findViewById<TextView>(R.id.selectedFileTextView)
            val activity = view.context as? ServiceControlActivity
            var selectedFileUri: Uri? = null
            var selectedFirmwareFile: File? = null

            fun showCloudFilePicker(activity: ServiceControlActivity) {
                MqttManager.retrieveAvailableFirmware(
                    "BLE_FoTA",
                    onResult = { cloudFiles ->
                        activity.runOnUiThread {
                            val bottomSheetView = activity.layoutInflater.inflate(R.layout.bottom_sheet_file_selector, null)
                            val dialog = BottomSheetDialog(activity)
                            dialog.setContentView(bottomSheetView)

                            val recyclerView = bottomSheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.cloudFileRecyclerView)
                            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
                            recyclerView.adapter = CloudFileAdapter(cloudFiles) { selectedFile ->
                                val deviceId = gatt.device.address

                                MqttManager.sendFirmwareRequest(deviceId, selectedFile.version)
                                MqttManager.subscribeToFirmwareForDevice(deviceId) { firmwareBytes ->
                                    activity.runOnUiThread {
                                        selectedFirmwareFile = File.createTempFile("firmware_", ".bin", activity.cacheDir)
                                        selectedFirmwareFile!!.writeBytes(firmwareBytes)
                                        selectedFileUri = null
                                        selectedFileTextView.text = selectedFile.name
                                        Toast.makeText(activity, "✅ Firmware ready: ${selectedFile.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                dialog.dismiss()
                            }
                            dialog.show()
                        }
                    },
                    onError = { error ->
                        activity.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "⚠️ Failed to fetch firmware list: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }

            if (activity != null) {
                selectFileButton.setOnClickListener {
                    activity.launchFilePicker { uri: Uri ->
                        selectedFileUri = uri
                        selectedFirmwareFile = null
                        val fileName = FilePickerHelper.getFileNameFromUri(activity, uri)
                        selectedFileTextView.text = fileName ?: "Unknown file"
                    }
                }

                selectCloudFileButton.setOnClickListener {
                    showCloudFilePicker(activity)
                }

                val startUpdateButton = view.findViewById<Button>(R.id.startUpdateButton)
                startUpdateButton.setOnClickListener {
                    val binary: ByteArray? = when {
                        selectedFileUri != null -> {
                            activity.contentResolver.openInputStream(selectedFileUri!!)?.use { it.readBytes() }
                        }
                        selectedFirmwareFile != null -> {
                            selectedFirmwareFile!!.readBytes()
                        }
                        else -> {
                            statusText.text = "No file selected"
                            return@setOnClickListener
                        }
                    }

                    if (binary == null) {
                        statusText.text = "Failed to read file"
                        return@setOnClickListener
                    }

                    val mtu = ConnectionManager.negotiatedMtu
                    val chunkSize = mtu - 3

                    val baseAddressChar = gatt.getCharacteristic(
                        UUID.fromString(ble_fota_base_address_characteristic_uuid)
                    )
                    val otaDataChar = gatt.getCharacteristic(
                        UUID.fromString(ble_fota_raw_data_characteristic_uuid)
                    )

                    if (baseAddressChar == null || otaDataChar == null) {
                        statusText.text = "OTA characteristics not found"
                        return@setOnClickListener
                    }

                    otaStartTime = System.currentTimeMillis()

                    baseAddressChar.writeType =
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    baseAddressChar.value = byteArrayOf(0x02, 0x00, 0x00, 0x00)
                    ConnectionManager.writeCharacteristic(baseAddressChar)
                    Thread.sleep(20)

                    val chunks = binary.toList().chunked(chunkSize).map { it.toByteArray() }
                    progressBar.max = chunks.size
                    progressBar.progress = 0

                    otaDataChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                    Thread {
                        chunks.forEachIndexed { index, chunk ->
                            otaDataChar.value = chunk
                            ConnectionManager.bluetoothGatt?.writeCharacteristic(otaDataChar)
                            Thread.sleep(50)
                            activity.runOnUiThread {
                                progressBar.progress = index + 1
                                selectedFileTextView.text =
                                    "Chunk $index of ${chunks.size} written"
                            }
                        }

                        baseAddressChar.value = byteArrayOf(0x07, 0x00, 0x00, 0x00)
                        ConnectionManager.writeCharacteristic(baseAddressChar)
                        Thread.sleep(20)

                        otaEndTime = System.currentTimeMillis()
                        val seconds = (otaEndTime - otaStartTime) / 1000

                        activity.runOnUiThread {
                            AlertDialog.Builder(activity)
                                .setTitle("Upload Sent")
                                .setMessage("OTA complete. Sent in $seconds seconds. Reboot should follow.")
                                .setPositiveButton("OK") { _, _ ->
                                    val intent = Intent(activity, ScanActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    activity.startActivity(intent)
                                    activity.finish()
                                }
                                .show()

                        }
                    }.start()
                }
            } else {
                selectFileButton.isEnabled = false
                selectedFileTextView.text = "File picker unavailable"
            }
        }
    ),

    DiscoveredFeature(
        name = "Req BLE FoTA",
        layoutResId = R.layout.feature_reboot_request,
        serviceUUIDs = listOf(UUID.fromString(ble_fota_service_uuid)),
        characteristicUUIDs = listOf(UUID.fromString(ble_fota_reboot_request_characteristic_uuid)),
        binder = { view, gatt ->
            val rebootChar = gatt.getCharacteristic(UUID.fromString(ble_fota_reboot_request_characteristic_uuid))
            val button = view.findViewById<Button>(R.id.rebootButton)
            val statusText = view.findViewById<TextView>(R.id.rebootStatusText)

            if (rebootChar == null) {
                button.isEnabled = false
                statusText.text = "Characteristic not found"
                return@DiscoveredFeature
            }

            button.setOnClickListener {
                rebootChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                rebootChar.value = byteArrayOf(0x01, 0x10, 0x70)
                ConnectionManager.writeCharacteristic(rebootChar)
                statusText.text = "Reboot command sent"

                button.postDelayed({
                    val context = view.context
                    if (context is ServiceControlActivity && !context.isFinishing) {
                        ConnectionManager.bluetoothGatt?.refresh()
                        ConnectionManager.bluetoothGatt?.close()
                        ConnectionManager.bluetoothGatt = null

                        val intent = Intent(context, ScanActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        context.startActivity(intent)
                        context.finish()
                    }
                }, 200)
            }
        }
    ),
    DiscoveredFeature(
        name = "STM OTA",
        layoutResId = R.layout.feature_stm_ota,
        serviceUUIDs = listOf(UUID.fromString(stm_ota_service_uuid)),
        characteristicUUIDs = listOf(
            UUID.fromString(stm_ota_base_address_characteristic_uuid),
            UUID.fromString(stm_ota_file_upload_reboot_confirmation_characteristic_uuid.toString()),
            UUID.fromString(stm_ota_ota_raw_data_characteristic_uuid)
        ),
        binder = { view, gatt ->
            val statusText = view.findViewById<TextView>(R.id.statusTextView)
            val progressBar = view.findViewById<ProgressBar>(R.id.updateProgressBar)
            val selectFileButton = view.findViewById<Button>(R.id.selectFileButton)
            val selectCloudFileButton = view.findViewById<Button>(R.id.selectCloudFileButton)
            val selectedFileTextView = view.findViewById<TextView>(R.id.selectedFileTextView)
            val activity = view.context as? ServiceControlActivity
            var selectedFileUri: Uri? = null
            var selectedFirmwareFile: File? = null

            fun showCloudFilePicker(activity: ServiceControlActivity) {
                MqttManager.retrieveAvailableFirmware(
                    "STM",
                    onResult = { cloudFiles ->
                        activity.runOnUiThread {
                            val bottomSheetView = activity.layoutInflater.inflate(R.layout.bottom_sheet_file_selector, null)
                            val dialog = BottomSheetDialog(activity)
                            dialog.setContentView(bottomSheetView)

                            val recyclerView = bottomSheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.cloudFileRecyclerView)
                            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
                            recyclerView.adapter = CloudFileAdapter(cloudFiles) { selectedFile ->
                                val deviceId = gatt.device.address

                                MqttManager.sendFirmwareRequest(deviceId, selectedFile.version)
                                MqttManager.subscribeToFirmwareForDevice(deviceId) { firmwareBytes ->
                                    activity.runOnUiThread {
                                        selectedFirmwareFile = File.createTempFile("firmware_", ".bin", activity.cacheDir)
                                        selectedFirmwareFile!!.writeBytes(firmwareBytes)
                                        selectedFileUri = null
                                        selectedFileTextView.text = selectedFile.name
                                        Toast.makeText(activity, "✅ Firmware ready: ${selectedFile.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                dialog.dismiss()
                            }
                            dialog.show()
                        }
                    },
                    onError = { error ->
                        activity.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "⚠️ Failed to fetch firmware list: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }

            if (activity != null) {
                selectFileButton.setOnClickListener {
                    activity.launchFilePicker { uri: Uri ->
                        selectedFileUri = uri
                        selectedFirmwareFile = null
                        val fileName = FilePickerHelper.getFileNameFromUri(activity, uri)
                        selectedFileTextView.text = fileName ?: "Unknown file"
                    }
                }

                selectCloudFileButton.setOnClickListener {
                    showCloudFilePicker(activity)
                }

                val startUpdateButton = view.findViewById<Button>(R.id.startUpdateButton)
                startUpdateButton.setOnClickListener {
                    val binary: ByteArray? = when {
                        selectedFileUri != null -> {
                            activity.contentResolver.openInputStream(selectedFileUri!!)?.use { it.readBytes() }
                        }
                        selectedFirmwareFile != null -> {
                            selectedFirmwareFile!!.readBytes()
                        }
                        else -> {
                            statusText.text = "No file selected"
                            return@setOnClickListener
                        }
                    }

                    if (binary == null) {
                        statusText.text = "Failed to read file"
                        return@setOnClickListener
                    }

                    val mtu = ConnectionManager.negotiatedMtu
                    val chunkSize = mtu - 3

                    val baseAddressChar = gatt.getCharacteristic(UUID.fromString(stm_ota_base_address_characteristic_uuid))
                    val otaDataChar = gatt.getCharacteristic(UUID.fromString(stm_ota_ota_raw_data_characteristic_uuid))
                    val confirmationChar = gatt.getCharacteristic(UUID.fromString(stm_ota_file_upload_reboot_confirmation_characteristic_uuid.toString()))

                    if (baseAddressChar == null || otaDataChar == null || confirmationChar == null) {
                        statusText.text = "OTA characteristics not found"
                        return@setOnClickListener
                    }

                    ConnectionManager.enableIndications(confirmationChar)
                    ConnectionManager.onOtaConfirmed = {
                        otaEndTime = System.currentTimeMillis()
                        val durationMillis = otaEndTime - otaStartTime
                        val seconds = durationMillis / 1000

                        if (!activity.isFinishing) {
                            ConnectionManager.bluetoothGatt?.refresh()
                            ConnectionManager.bluetoothGatt?.close()
                            ConnectionManager.bluetoothGatt = null

                            AlertDialog.Builder(activity)
                                .setTitle("Update complete")
                                .setMessage("The device has confirmed the OTA-update. \nOTA-update completed in $seconds seconds.\nReturning to scan activity.")
                                .setCancelable(false)
                                .setPositiveButton("OK") { _, _ ->
                                    val intent = Intent(activity, ScanActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    activity.startActivity(intent)
                                    activity.finish()
                                }
                                .show()
                        }
                    }
                    ConnectionManager.startOtaProcedure = {
                        otaStartTime = System.currentTimeMillis()

                        baseAddressChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        baseAddressChar.value = byteArrayOf(0x02, 0x00, 0x70, 0x00)
                        ConnectionManager.writeCharacteristic(baseAddressChar)
                        Thread.sleep(20)

                        val chunks = binary.toList().chunked(chunkSize).map { it.toByteArray() }
                        progressBar.max = chunks.size
                        progressBar.progress = 0

                        otaDataChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                        Thread {
                            val handler = Handler(Looper.getMainLooper())
                            var confirmationReceived = false

                            chunks.forEachIndexed { index, chunk ->
                                otaDataChar.value = chunk
                                ConnectionManager.bluetoothGatt?.writeCharacteristic(otaDataChar)
                                Thread.sleep(20)
                                activity.runOnUiThread {
                                    progressBar.progress = index + 1
                                    selectedFileTextView.text = "Chunk $index of ${chunks.size} written"
                                }
                            }

                            baseAddressChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            baseAddressChar.value = byteArrayOf(0x07, 0x00, 0x70, 0x00)
                            ConnectionManager.writeCharacteristic(baseAddressChar)
                            Thread.sleep(20)

                            activity.runOnUiThread {
                                statusText.text = "Awaiting confirmation..."
                            }

                            // Start a timeout: fail if no confirmation received within 5 seconds
                            handler.postDelayed({
                                if (!confirmationReceived) {
                                    activity.runOnUiThread {
                                        android.widget.Toast.makeText(activity, "❌ Update failed: no confirmation received", android.widget.Toast.LENGTH_LONG).show()
                                        selectedFileTextView.text = "No file selected"
                                        progressBar.progress = 0
                                        statusText.text = "Update failed"
                                    }
                                }
                            }, 5000)

                            // This runs when confirmation is received
                            ConnectionManager.onOtaConfirmed = {
                                confirmationReceived = true
                                otaEndTime = System.currentTimeMillis()
                                val durationMillis = otaEndTime - otaStartTime
                                val seconds = durationMillis / 1000

                                if (!activity.isFinishing) {
                                    ConnectionManager.bluetoothGatt?.refresh()
                                    ConnectionManager.bluetoothGatt?.close()
                                    ConnectionManager.bluetoothGatt = null

                                    AlertDialog.Builder(activity)
                                        .setTitle("Update complete")
                                        .setMessage("The device has confirmed the OTA-update. \nOTA-update completed in $seconds seconds.\nReturning to scan activity.")
                                        .setCancelable(false)
                                        .setPositiveButton("OK") { _, _ ->
                                            val intent = Intent(activity, ScanActivity::class.java)
                                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            activity.startActivity(intent)
                                            activity.finish()
                                        }
                                        .show()
                                }
                            }
                        }.start()
                    }
                }
            } else {
                selectFileButton.isEnabled = false
                selectedFileTextView.text = "File picker unavailable"
            }
        }
    ),
    DiscoveredFeature(
        name = "Reboot Request",
        layoutResId = R.layout.feature_reboot_request,
        characteristicUUIDs = listOf(UUID.fromString(stm_ota_reboot_request_characteristic_uuid)),
        binder = { view, gatt ->
            val rebootCharUUID = UUID.fromString(stm_ota_reboot_request_characteristic_uuid)
            val rebootChar = gatt.services
                .flatMap { it.characteristics }
                .find { it.uuid == rebootCharUUID }

            val button = view.findViewById<Button>(R.id.rebootButton)
            val statusText = view.findViewById<TextView>(R.id.rebootStatusText)

            if (rebootChar == null) {
                button.isEnabled = false
                statusText.text = "Characteristic not found"
                return@DiscoveredFeature
            }

            button.setOnClickListener {
                val value = byteArrayOf(0x01, 0x07, 0xFF.toByte()) // 0x0107FF
                rebootChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                rebootChar.value = value
                ConnectionManager.writeCharacteristic(rebootChar)

                statusText.text = "Reboot command sent"

                button.postDelayed({
                    val context = view.context
                    if (context is ServiceControlActivity && !context.isFinishing) {
                        ConnectionManager.bluetoothGatt?.refresh()
                        ConnectionManager.bluetoothGatt?.close()
                        ConnectionManager.bluetoothGatt = null

                        AlertDialog.Builder(context)
                            .setTitle("Reboot request sent")
                            .setMessage("characteristic is of type WRITE_TYPE_NO_RESPONSE reset is assumed returning to scanning.")
                            .setCancelable(false)
                            .setPositiveButton("OK") { _, _ ->
                                val intent = Intent(context, ScanActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                context.startActivity(intent)
                                context.finish()
                            }
                            .show()
                    }
                }, 200)
            }
        }
    )
)

fun android.bluetooth.BluetoothGatt.getCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
    return this.services
        .flatMap { it.characteristics }
        .firstOrNull { it.uuid == uuid }
}