package com.example.bl_ota.ui.adapters

import android.bluetooth.BluetoothGatt
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.example.bl_ota.DiscoveredFeature

class FeatureListAdapter(
    private val context: Context,
    private val features: List<DiscoveredFeature>,
    private val gatt: BluetoothGatt
) : BaseAdapter() {

    override fun getCount(): Int = features.size
    override fun getItem(position: Int): Any = features[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val feature = features[position]
        val view = LayoutInflater.from(context).inflate(feature.layoutResId, parent, false)
        feature.binder(view, gatt)
        return view
    }
}
