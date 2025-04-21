package com.example.bl_ota

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

        holder.deviceNameTextView.text = currentItem.name
        holder.deviceAddressTextView.text = currentItem.address

        val sdf_yymmdd = SimpleDateFormat("YY/MM/dd", Locale.getDefault())
        val seenText_yymmdd = sdf_yymmdd.format(Date(currentItem.lastSeen))
        holder.lastSeenTextView_yymmdd.text = seenText_yymmdd

        val sdf_hhmmss = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val seenText_hhmmss = sdf_hhmmss.format(Date(currentItem.lastSeen))
        holder.lastSeenTextView_hhmmss.text = seenText_hhmmss

        holder.RSSITextView.text = "${currentItem.rssi}"
        holder.barsImageView.setImageDrawable(
            getBarsImageFromRssi(holder.itemView.context, currentItem.rssi)
        )
        holder.deviceTypeImageView.setImageDrawable(
            getDeviceTypeImage(holder.itemView.context, currentItem.deviceType)
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
        val RSSITextView: TextView = itemView.findViewById(R.id.RSSITextView)
        val deviceTypeImageView: ImageView = itemView.findViewById(R.id.DeviceTypeImageView)
        val deviceNameTextView: TextView = itemView.findViewById(R.id.DeviceNameTextView)
        val deviceAddressTextView: TextView = itemView.findViewById(R.id.DeviceAddressTextView)
        val lastSeenTextView_yymmdd: TextView = itemView.findViewById(R.id.LastSeenTextView_yymmdd)
        val lastSeenTextView_hhmmss: TextView = itemView.findViewById(R.id.LastSeenTextView_hhmmss)
    }
}