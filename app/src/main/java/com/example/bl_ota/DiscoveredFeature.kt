package com.example.bl_ota
import android.bluetooth.BluetoothGatt
import android.view.View
import java.util.UUID

data class DiscoveredFeature(
    val name: String,
    val layoutResId: Int,
    val serviceUUIDs: List<UUID> = emptyList(),
    val characteristicUUIDs: List<UUID> = emptyList(),
    val matchAllServiceUUIDs: Boolean = true,
    val matchAllCharacteristicUUIDs: Boolean = true,
    val binder: (View, BluetoothGatt) -> Unit
)
