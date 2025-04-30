package com.example.bl_ota

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceAdapter(
    private val deviceList: ArrayList<DeviceData>,
    private val onItemClick: (DeviceData) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.devicelist_layout, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentItem = deviceList[position]
        val context = holder.itemView.context

        holder.deviceNameTextView.text = currentItem.name
        holder.deviceAddressTextView.text = currentItem.address

        val seenTextYMD = sdfYMD.format(Date(currentItem.lastSeen))
        holder.lastSeenTextViewYMD.text = seenTextYMD

        val seenTextHMS = sdfHMS.format(Date(currentItem.lastSeen))
        holder.lastSeenTextViewHMS.text = seenTextHMS

        holder.rssiTextView.text = context.getString(R.string.rssi_value, currentItem.rssi)

        holder.barsImageView.setImageDrawable(
            getBarsImageFromRssi(context, currentItem.rssi)
        )
        holder.deviceTypeImageView.setImageDrawable(
            getDeviceTypeImage(context, currentItem.deviceType)
        )

        holder.deviceNameTextView.isSelected = true
        holder.deviceAddressTextView.isSelected = true

        holder.itemView.setOnClickListener {
            onItemClick(currentItem)
        }
    }

    override fun getItemCount(): Int = deviceList.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val barsImageView: ImageView = itemView.findViewById(R.id.BarsImageView)
        val rssiTextView: TextView = itemView.findViewById(R.id.RSSITextView)
        val deviceTypeImageView: ImageView = itemView.findViewById(R.id.DeviceTypeImageView)
        val deviceNameTextView: TextView = itemView.findViewById(R.id.DeviceNameTextView)
        val deviceAddressTextView: TextView = itemView.findViewById(R.id.DeviceAddressTextView)
        val lastSeenTextViewYMD: TextView = itemView.findViewById(R.id.LastSeenTextView_yymmdd)
        val lastSeenTextViewHMS: TextView = itemView.findViewById(R.id.LastSeenTextView_hhmmss)
    }

    @SuppressLint("ConstantLocale")
    companion object {
        private val sdfYMD = SimpleDateFormat("yy/MM/dd", Locale.getDefault())
        private val sdfHMS = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
}
