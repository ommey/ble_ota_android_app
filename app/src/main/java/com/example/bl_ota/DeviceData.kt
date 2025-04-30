package com.example.bl_ota

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

enum class DeviceTypes{
    KNOWN,
    UNKNOWN
}

data class DeviceData(
    var deviceType: DeviceTypes, var rssi: Int, var name: String, var address: String, var lastSeen: Long
)

fun getBarsImageFromRssi(context: Context, rssi: Int): Drawable? {
    val resId = when {
        rssi >= -50 -> R.drawable.four_bars     // Excellent
        rssi >= -70 -> R.drawable.three_bars    // Good
        rssi >= -85 -> R.drawable.two_bars      // Fair
        rssi >= -100 -> R.drawable.one_bars     // Weak
        else -> R.drawable.no_bars              // Very weak or unknown
    }

    return ContextCompat.getDrawable(context, resId)
}

fun getDeviceTypeImage(context: Context, deviceType: DeviceTypes): Drawable?{
    val resId = when {
        deviceType == DeviceTypes.KNOWN -> R.drawable.saved
        else -> R.drawable.device
    }
    return ContextCompat.getDrawable(context, resId)
}
