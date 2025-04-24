package com.example.bl_ota

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.example.bl_ota.R
import com.example.bl_ota.DiscoveredFeature
import com.example.bl_ota.FilePickerHelper
import com.example.bl_ota.ServiceControlActivity
import java.net.URI
import java.util.*

import java.util.UUID
val stm_ota_service_uuid : String = "0000FE20-cc7a-482a-984a-7f2ed5b3e58f"
val stm_ota_base_address_characteristic_uuid : String = "000FE22-8e22-4541-9d4c-21edae82ed19"
val stm_ota_file_upload_reboot_confirmation_characteristic_uuid = UUID.fromString("000FE23-8e22-4541-9d4c-21edae82ed19")
val stm_ota_ota_raw_data_characteristic_uuid : String = "000FE24-8e22-4541-9d4c-21edae82ed19"
val stm_ota_reboot_request_characteristic_uuid : String = "0000FE11-8e22-4541-9d4c-21edae82ed19"

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@SuppressLint("MissingPermission")
val featureCatalog = listOf(
    // STM OTA: needs all service UUIDs
    DiscoveredFeature(
        name = "STM OTA",
        layoutResId = R.layout.feature_stm_ota,
        serviceUUIDs = listOf(UUID.fromString(stm_ota_service_uuid)),
        characteristicUUIDs = listOf(
            UUID.fromString(stm_ota_base_address_characteristic_uuid),
            UUID.fromString(stm_ota_file_upload_reboot_confirmation_characteristic_uuid.toString()),
            UUID.fromString(stm_ota_ota_raw_data_characteristic_uuid)),
        binder = { view, gatt ->
            val statusText = view.findViewById<TextView>(R.id.statusTextView)
            val progressBar = view.findViewById<ProgressBar>(R.id.updateProgressBar)
            val selectFileButton = view.findViewById<Button>(R.id.selectFileButton)
            val selectedFileTextView = view.findViewById<TextView>(R.id.selectedFileTextView)
            val activity = view.context as? ServiceControlActivity
            var selectedFileUri: Uri? = null
            if (activity != null) {

                selectFileButton.setOnClickListener {
                    selectFileButton.setOnClickListener {
                        activity.launchFilePicker { uri: Uri ->
                            selectedFileUri = uri
                            val fileName = FilePickerHelper.getFileNameFromUri(activity, uri)
                            selectedFileTextView.text = fileName ?: "Unknown file"
                        }
                    }

                }

                if (activity != null) {
                    selectFileButton.setOnClickListener {
                        activity.launchFilePicker { uri: Uri ->
                            selectedFileUri = uri
                            val fileName = FilePickerHelper.getFileNameFromUri(activity, uri)
                            selectedFileTextView.text = fileName ?: "Unknown file"
                        }
                    }

                    val startUpdateButton = view.findViewById<Button>(R.id.startUpdateButton)
                    startUpdateButton.setOnClickListener {
                        val uri = selectedFileUri
                        if (uri == null) {
                            statusText.text = "No file selected"
                            return@setOnClickListener
                        }

                        val inputStream = activity.contentResolver.openInputStream(uri)
                        val binary = inputStream?.readBytes()
                        inputStream?.close()

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

                        // Step 1: Write base address
                        baseAddressChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        baseAddressChar.value = byteArrayOf(0x02, 0x00, 0x70, 0x00)
                        ConnectionManager.writeCharacteristic(baseAddressChar)
                        Thread.sleep(20) // small delay to not overload


                        // Step 2: Send OTA chunks
                        val chunks = binary.toList().chunked(chunkSize).map { it.toByteArray() }
                        progressBar.max = chunks.size
                        progressBar.progress = 0

                        otaDataChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                        Thread {
                            chunks.forEachIndexed { index, chunk ->
                                otaDataChar.value = chunk
                                ConnectionManager.bluetoothGatt?.writeCharacteristic(otaDataChar)
                                Thread.sleep(40) // small delay to not overload
                                activity.runOnUiThread {
                                    progressBar.progress = index + 1
                                }
                            }



                            baseAddressChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            baseAddressChar.value = byteArrayOf(0x07, 0x00, 0x70, 0x00)
                            ConnectionManager.writeCharacteristic(baseAddressChar)
                            Thread.sleep(20) // small delay to not overload


                            // Step 3: Enable indication on confirmation characteristic
                            ConnectionManager.enableIndications(confirmationChar)

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
            }
        }
    )
)

fun android.bluetooth.BluetoothGatt.getCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
    return this.services
        .flatMap { it.characteristics }
        .firstOrNull { it.uuid == uuid }
}

