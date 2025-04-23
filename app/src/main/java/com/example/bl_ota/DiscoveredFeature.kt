package com.example.bl_ota

import android.bluetooth.BluetoothGatt
import android.view.View
import java.util.*

data class DiscoveredFeature(
    val name: String,
    val serviceUUIDs: List<UUID>,
    val characteristicUUIDs: List<UUID>,
    val layoutResId: Int,
    val binder: (View, BluetoothGatt) -> Unit
)
