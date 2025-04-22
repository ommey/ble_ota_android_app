package com.example.bl_ota

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.bl_ota.ble.ConnectionManager

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

        capLayout.removeAllViews()

        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
            val readBlock = inflater.inflate(R.layout.read_block, capLayout, false)
            val readBtn = readBlock.findViewById<Button>(R.id.readBtn)
            val output = readBlock.findViewById<TextView>(R.id.readOutput)

            readBtn.setOnClickListener {
                ConnectionManager.readCharacteristic(characteristic)
                Toast.makeText(context, "Reading...", Toast.LENGTH_SHORT).show()
            }

            capLayout.addView(readBlock)
        }

        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            val writeBlock = inflater.inflate(R.layout.write_block, capLayout, false)
            val input = writeBlock.findViewById<EditText>(R.id.writeInput)
            val sendBtn = writeBlock.findViewById<Button>(R.id.sendWriteBtn)

            sendBtn.setOnClickListener {
                val text = input.text.toString()
                val bytes = text.toByteArray()
                characteristic.value = bytes
                ConnectionManager.writeCharacteristic(characteristic)
                Toast.makeText(context, "Sent: $text", Toast.LENGTH_SHORT).show()
            }

            capLayout.addView(writeBlock)
        }

        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            val notifyBlock = inflater.inflate(R.layout.notify_block, capLayout, false)
            val notifyBtn = notifyBlock.findViewById<Button>(R.id.notifyToggleBtn)
            notifyBtn.setOnClickListener {
                ConnectionManager.toggleNotifications(characteristic)
                Toast.makeText(context, "Notify toggled", Toast.LENGTH_SHORT).show()
            }
            capLayout.addView(notifyBlock)
        }

        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            val indicateBlock = inflater.inflate(R.layout.indicate_block, capLayout, false)
            val indicateBtn = indicateBlock.findViewById<Button>(R.id.indicateToggleBtn)
            indicateBtn.setOnClickListener {
                ConnectionManager.toggleIndications(characteristic)
                Toast.makeText(context, "Indicate toggled", Toast.LENGTH_SHORT).show()
            }
            capLayout.addView(indicateBlock)
        }

        // Set up toggle behavior
        view.setOnClickListener {
            val isVisible = capLayout.isVisible

            capLayout.visibility = if (isVisible) View.GONE else View.VISIBLE

            // Rotate the arrow accordingly
            childArrow.animate()
                .rotation(if (isVisible) 0f else 180f)
                .setDuration(200)
                .start()
        }

        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            val writeNoRspBlock = inflater.inflate(R.layout.write_no_response_block, capLayout, false)
            val input = writeNoRspBlock.findViewById<EditText>(R.id.writeNoRspInput)
            val sendBtn = writeNoRspBlock.findViewById<Button>(R.id.sendWriteNoRspBtn)

            sendBtn.setOnClickListener {
                val text = input.text.toString()
                val bytes = text.toByteArray()
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                characteristic.value = bytes
                ConnectionManager.writeCharacteristic(characteristic)
                Toast.makeText(context, "Sent (No Rsp): $text", Toast.LENGTH_SHORT).show()
            }

            capLayout.addView(writeNoRspBlock)
        }



        // Reset arrow rotation when view is reused
        childArrow.rotation = if (capLayout.isVisible) 180f else 0f

        return view
    }
}
