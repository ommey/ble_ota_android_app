package com.example.bl_ota

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class ExpandableServiceListAdapter(
    private val context: Context,
    private val serviceData: Map<BluetoothGattService, List<BluetoothGattCharacteristic>>
) : BaseExpandableListAdapter() {

    private val serviceList = serviceData.keys.toList()

    override fun getGroupCount(): Int = serviceList.size

    override fun getChildrenCount(groupPosition: Int): Int {
        return serviceData[serviceList[groupPosition]]?.size ?: 0
    }

    override fun getGroup(groupPosition: Int): Any = serviceList[groupPosition]

    override fun getChild(groupPosition: Int, childPosition: Int): Any {
        return serviceData[serviceList[groupPosition]]?.get(childPosition) ?: ""
    }

    override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

    override fun getChildId(groupPosition: Int, childPosition: Int): Long = childPosition.toLong()

    override fun hasStableIds(): Boolean = false

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true

    override fun getGroupView(
        groupPosition: Int,
        isExpanded: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.layout_group, parent, false)

        val service = getGroup(groupPosition) as BluetoothGattService

        val serviceTextView = view.findViewById<TextView>(R.id.serviceUUIDTextView)
        serviceTextView.text = service.uuid.toString()
        serviceTextView.isSelected = true
        val expandArrow = view.findViewById<ImageView>(R.id.expandArrow)
        val aliasTextView = view.findViewById<TextView>(R.id.customAliasTextView)
        aliasTextView.text = "" // Set alias if needed
        aliasTextView.isSelected = true

        expandArrow.animate().rotation(if (isExpanded) 180f else 0f).setDuration(200).start()

        return view
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val inflater = LayoutInflater.from(context)
        val view = convertView ?: inflater.inflate(R.layout.layout_child, parent, false)

        val headerLayout = view.findViewById<View>(R.id.characteristicHeader)
        headerLayout.visibility = if (childPosition == 0) View.VISIBLE else View.GONE

        val characteristic = getChild(groupPosition, childPosition) as BluetoothGattCharacteristic

        val charTextView = view.findViewById<TextView>(R.id.characteristicUUIDTextView)
        val aliasTextView = view.findViewById<TextView>(R.id.customAliasTextView)
        val capLayout = view.findViewById<LinearLayout>(R.id.capabilityLayout)
        val childArrow = view.findViewById<ImageView>(R.id.childArrow)

        charTextView.text = characteristic.uuid.toString()
        aliasTextView.text = ""
        charTextView.isSelected = true
        aliasTextView.isSelected = true

        // Capabilities info
        val read = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
        val write = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        val notify = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        val indicate = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

        view.findViewById<TextView>(R.id.capRead).text = "Read: ${if (read) "✅" else "❌"}"
        view.findViewById<TextView>(R.id.capWrite).text = "Write: ${if (write) "✅" else "❌"}"
        view.findViewById<TextView>(R.id.capNotify).text = "Notify: ${if (notify) "✅" else "❌"}"
        view.findViewById<TextView>(R.id.capIndicate).text = "Indicate: ${if (indicate) "✅" else "❌"}"

        // Set up toggle behavior
        view.setOnClickListener {
            val isVisible = capLayout.visibility == View.VISIBLE

            capLayout.visibility = if (isVisible) View.GONE else View.VISIBLE

            // Rotate the arrow accordingly
            childArrow.animate()
                .rotation(if (isVisible) 0f else 180f)
                .setDuration(200)
                .start()
        }

        // Reset arrow rotation when view is reused
        childArrow.rotation = if (capLayout.visibility == View.VISIBLE) 180f else 0f

        return view
    }
}
