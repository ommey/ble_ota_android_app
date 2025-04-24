package com.example.bl_ota

import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.example.bl_ota.R
import com.example.bl_ota.DiscoveredFeature
import com.example.bl_ota.FilePickerHelper
import com.example.bl_ota.ServiceControlActivity
import java.net.URI
import java.util.*

import java.util.UUID

val featureCatalog = listOf(
    // STM OTA: needs all service UUIDs
    DiscoveredFeature(
        name = "STM OTA",
        layoutResId = R.layout.feature_stm_ota,
        serviceUUIDs = listOf(UUID.fromString("0000FE20-cc7a-482a-984a-7f2ed5b3e58f")),
        characteristicUUIDs = listOf(
            UUID.fromString("000FE22-8e22-4541-9d4c-21edae82ed19"),
            UUID.fromString("000FE23-8e22-4541-9d4c-21edae82ed19"),
            UUID.fromString("000FE24-8e22-4541-9d4c-21edae82ed19")),
        binder = { view, gatt ->
            val statusText = view.findViewById<TextView>(R.id.statusTextView)
            val progressBar = view.findViewById<ProgressBar>(R.id.updateProgressBar)
            val selectFileButton = view.findViewById<Button>(R.id.selectFileButton)
            val selectedFileTextView = view.findViewById<TextView>(R.id.selectedFileTextView)

            val activity = view.context as? ServiceControlActivity
            if (activity != null) {
                selectFileButton.setOnClickListener {
                    activity.launchFilePicker { uri : Uri->
                        val fileName = FilePickerHelper.getFileNameFromUri(activity, uri)
                        selectedFileTextView.text = fileName ?: "Unknown file"
                        // 👉 You can save `uri` somewhere if needed for OTA logic
                    }
                }
            } else {
                selectFileButton.isEnabled = false
                selectedFileTextView.text = "File picker unavailable"
            }
        }

    ),

    // Reboot Request: only one characteristic needed
    DiscoveredFeature(
        name = "Reboot Request",
        layoutResId = R.layout.feature_reboot_request,
        serviceUUIDs = emptyList(),
        characteristicUUIDs = listOf(UUID.fromString("0000FE11-8e22-4541-9d4c-21edae82ed19")),
        binder = { view, gatt -> /* reboot logic */ }
    )
)

