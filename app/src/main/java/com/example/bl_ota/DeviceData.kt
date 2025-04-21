package com.example.bl_ota

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

enum class DeviceTypes{
    KNOWN,
    UNKNOWN
}

enum class Bars{
    NOBARS,
    ONEBARS,
    TWOBARS,
    THREEBARS,
    FOURBARS,
}

data class DeviceData(
    var deviceType: DeviceTypes, var rssi: Int, var name: String, var address: String, var lastSeen: Long
){

}

fun getBarsImagefromEnum(context: Context, bars: Bars): Drawable? {
    val resId = when (bars) {
        Bars.NOBARS -> R.drawable.no_bars
        Bars.ONEBARS -> R.drawable.one_bars
        Bars.TWOBARS -> R.drawable.two_bars
        Bars.THREEBARS -> R.drawable.three_bars
        Bars.FOURBARS -> R.drawable.four_bars
    }

    return ContextCompat.getDrawable(context, resId)
}

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
