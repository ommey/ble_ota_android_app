package com.example.bl_ota.util

import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.example.bl_ota.R
import com.example.bl_ota.DiscoveredFeature
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
        binder = { view, gatt -> /* your logic */ }
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

