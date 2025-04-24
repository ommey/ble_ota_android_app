package com.example.bl_ota

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.net.Uri
import android.os.Build
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.util.UUID

const val stm_ota_service_uuid: String = "0000FE20-cc7a-482a-984a-7f2ed5b3e58f"
const val stm_ota_base_address_characteristic_uuid: String = "000FE22-8e22-4541-9d4c-21edae82ed19"
const val stm_ota_ota_raw_data_characteristic_uuid: String = "000FE24-8e22-4541-9d4c-21edae82ed19"
const val stm_ota_reboot_request_characteristic_uuid: String = "0000FE11-8e22-4541-9d4c-21edae82ed19"

val stm_ota_file_upload_reboot_confirmation_characteristic_uuid: UUID? =
    UUID.fromString("000FE23-8e22-4541-9d4c-21edae82ed19")

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@SuppressLint("MissingPermission", "SetTextI18n")
val featureCatalog = listOf(
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
                    onResult = { cloudFiles ->
                        activity.runOnUiThread {
                            val bottomSheetView = activity.layoutInflater.inflate(
                                R.layout.bottom_sheet_file_selector, null
                            )
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
                                        android.widget.Toast.makeText(activity, "✅ Firmware ready: ${selectedFile.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                dialog.dismiss()
                            }

                            dialog.show()
                        }
                    },
                    onError = { error ->
                        activity.runOnUiThread {
                            android.widget.Toast.makeText(
                                activity,
                                "⚠️ Failed to fetch firmware list: ${error.message}",
                                android.widget.Toast.LENGTH_LONG
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

                    ConnectionManager.startOtaProcedure = {
                        baseAddressChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        baseAddressChar.value = byteArrayOf(0x02, 0x00, 0x70, 0x00)
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
                                Thread.sleep(40)
                                activity.runOnUiThread {
                                    progressBar.progress = index + 1
                                }
                            }

                            baseAddressChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            baseAddressChar.value = byteArrayOf(0x07, 0x00, 0x70, 0x00)
                            ConnectionManager.writeCharacteristic(baseAddressChar)
                            Thread.sleep(20)

                            activity.runOnUiThread {
                                statusText.text = "Awaiting confirmation..."
                            }
                        }.start()
                    }
                }
            } else {
                selectFileButton.isEnabled = false
                selectedFileTextView.text = "File picker unavailable"
            }
        }
    )
)

fun android.bluetooth.BluetoothGatt.getCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
    return this.services
        .flatMap { it.characteristics }
        .firstOrNull { it.uuid == uuid }
}