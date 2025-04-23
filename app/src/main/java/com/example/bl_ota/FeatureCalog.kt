package com.example.bl_ota.util

import android.bluetooth.BluetoothGattCharacteristic
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.example.bl_ota.R
import com.example.bl_ota.DiscoveredFeature
import com.example.bl_ota.ble.ConnectionManager
import java.util.*

import java.util.UUID

val featureCatalog = listOf(
    DiscoveredFeature(
        name = "STM OTA",
        layoutResId = R.layout.feature_stm_ota,
        serviceUUIDs = listOf(UUID.fromString("0000FE20-cc7a-482a-984a-7f2ed5b3e58f")),
        characteristicUUIDs = listOf(
            UUID.fromString("000FE22-8e22-4541-9d4c-21edae82ed19"),
            UUID.fromString("000FE23-8e22-4541-9d4c-21edae82ed19"),
            UUID.fromString("000FE24-8e22-4541-9d4c-21edae82ed19")),
        binder = { view, gatt -> /* existing logic */ }
    ),

    DiscoveredFeature(
        name = "Reboot Request",
        layoutResId = R.layout.feature_reboot_request,
        characteristicUUIDs = listOf(UUID.fromString("0000FE11-8e22-4541-9d4c-21edae82ed19")),
        binder = { view, gatt ->
            val rebootCharUUID = UUID.fromString("0000FE11-8e22-4541-9d4c-21edae82ed19")
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

