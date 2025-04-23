package com.example.bl_ota.util

import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.example.bl_ota.R
import com.example.bl_ota.DiscoveredFeature
import java.util.*

val featureCatalog = listOf(
    DiscoveredFeature(
        name = "STM OTA",
        serviceUUIDs = listOf(UUID.fromString("0000FE20-cc7a-482a-984a-7f2ed5b3e58f")),
        characteristicUUIDs = listOf(UUID.fromString("000FE22-8e22-4541-9d4c-21edae82ed19"),
            UUID.fromString("0000FE23-8e22-4541-9d4c-21edae82ed19"),
            UUID.fromString("0000FE24-8e22-4541-9d4c-21edae82ed19")),
        layoutResId = R.layout.feature_stm_ota,
        binder = { view, gatt ->
            val button = view.findViewById<Button>(R.id.startUpdateButton)
            val progressBar = view.findViewById<ProgressBar>(R.id.updateProgressBar)
            val statusText = view.findViewById<TextView>(R.id.statusTextView)

            button.setOnClickListener {
                statusText.text = "Starting update..."
                progressBar.visibility = View.VISIBLE
                // Insert your OTA logic here
            }
        }
    )
    // Add more known features here...
)
