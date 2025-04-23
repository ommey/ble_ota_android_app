package com.example.bl_ota.ui.adapters

import android.bluetooth.BluetoothGatt
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.TextView
import com.example.bl_ota.DiscoveredFeature
import com.example.bl_ota.R

class FeatureExpandableAdapter(
    private val context: Context,
    private val gatt: BluetoothGatt,
    private val featureList: List<DiscoveredFeature>
) : BaseExpandableListAdapter() {

    override fun getGroupCount(): Int = featureList.size
    override fun getChildrenCount(groupPosition: Int): Int = 1 // each feature has a single child layout
    override fun getGroup(groupPosition: Int): Any = featureList[groupPosition]
    override fun getChild(groupPosition: Int, childPosition: Int): Any = featureList[groupPosition]
    override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()
    override fun getChildId(groupPosition: Int, childPosition: Int): Long = childPosition.toLong()
    override fun hasStableIds(): Boolean = false
    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true

    override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.layout_known_service_group, parent, false)
        view.findViewById<TextView>(R.id.knownServiceNameTextView).text = featureList[groupPosition].name
        return view
    }

    override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup?): View {
        val feature = featureList[groupPosition]
        val view = LayoutInflater.from(context).inflate(feature.layoutResId, parent, false)
        feature.binder(view, gatt)
        return view
    }
}