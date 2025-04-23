package com.example.bl_ota

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import com.example.bl_ota.ble.ConnectionManager

class ExpandableServiceListAdapter(
    private val context: Context,
    private val serviceData: Map<BluetoothGattService, List<BluetoothGattCharacteristic>>
) : BaseExpandableListAdapter() {

    private val serviceList = serviceData.keys.toList()

    override fun getGroupCount(): Int = serviceList.size

    override fun getChildrenCount(groupPosition: Int): Int =
        serviceData[serviceList[groupPosition]]?.size ?: 0

    override fun getGroup(groupPosition: Int): Any = serviceList[groupPosition]

    override fun getChild(groupPosition: Int, childPosition: Int): Any =
        serviceData[serviceList[groupPosition]]?.get(childPosition) ?: ""

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
        view.findViewById<TextView>(R.id.serviceUUIDTextView).text = service.uuid.toString()
        view.findViewById<TextView>(R.id.customAliasTextView).text = ""
        view.findViewById<ImageView>(R.id.expandArrow).animate()
            .rotation(if (isExpanded) 180f else 0f).setDuration(200).start()

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

        val capLayout = view.findViewById<LinearLayout>(R.id.capabilityLayout)
        val childArrow = view.findViewById<ImageView>(R.id.childArrow)

        view.findViewById<TextView>(R.id.characteristicUUIDTextView).text = characteristic.uuid.toString()
        view.findViewById<TextView>(R.id.customAliasTextView).text = ""

        capLayout.removeAllViews()

        val props = characteristic.properties

        if ((props and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
            val readBlock = inflater.inflate(R.layout.read_block, capLayout, false)
            val readBtn = readBlock.findViewById<Button>(R.id.readBtn)
            val statusText = readBlock.findViewById<TextView>(R.id.readStatusText)
            val icon = readBlock.findViewById<ImageView>(R.id.readInteractionIcon)

            readBtn.setOnClickListener {
                // animate
                icon.setImageResource(R.drawable.cog)
                icon.rotation = 0f
                icon.animate().rotationBy(360f).setDuration(600).withEndAction {
                    icon.animate().rotationBy(360f).setDuration(600).start()
                }.start()

                statusText.text = "Reading..."
                ConnectionManager.pendingReadMap[characteristic.uuid] = statusText to icon
                ConnectionManager.readCharacteristic(characteristic)
            }
            capLayout.addView(readBlock)

        }

        if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            val writeBlock = inflater.inflate(R.layout.write_block, capLayout, false)
            val input = writeBlock.findViewById<EditText>(R.id.writeInput)
            val sendBtn = writeBlock.findViewById<Button>(R.id.sendWriteBtn)
            val statusText = writeBlock.findViewById<TextView>(R.id.writeResponse)
            val icon = writeBlock.findViewById<ImageView>(R.id.writeInteractionIcon)

            sendBtn.setOnClickListener {
                val text = input.text.toString().trim()
                input.text.clear()

                val bytes: ByteArray? = try {
                    when {
                        text.startsWith("0x", true) -> text.removePrefix("0x").chunked(2)
                            .map { it.toInt(16).toByte() }.toByteArray()
                        text.startsWith("0b", true) -> text.removePrefix("0b").chunked(8)
                            .map { it.toInt(2).toByte() }.toByteArray()
                        else -> text.toByteArray(Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    icon.setImageResource(R.drawable.fail)
                    statusText.text = "Failed to send: invalid input"
                    return@setOnClickListener
                }

                if (bytes == null || bytes.isEmpty()) {
                    icon.setImageResource(R.drawable.fail)
                    statusText.text = "Failed to send: empty input"
                    return@setOnClickListener
                }

                icon.setImageResource(R.drawable.cog)
                icon.rotation = 0f
                icon.animate().rotationBy(360f).setDuration(600).withEndAction {
                    icon.animate().rotationBy(360f).setDuration(600).start()
                }.start()

                ConnectionManager.pendingWriteMap[characteristic.uuid] = text
                ConnectionManager.pendingViewMap[characteristic.uuid] = statusText to icon

                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = bytes
                ConnectionManager.writeCharacteristic(characteristic)
            }

            capLayout.addView(writeBlock)
        }

        if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            val writeNoRspBlock = inflater.inflate(R.layout.write_no_response_block, capLayout, false)
            val input = writeNoRspBlock.findViewById<EditText>(R.id.writeNoRspInput)
            val sendBtn = writeNoRspBlock.findViewById<Button>(R.id.sendWriteNoRspBtn)
            val statusText = writeNoRspBlock.findViewById<TextView>(R.id.writeNoRspStatus)
            val icon = writeNoRspBlock.findViewById<ImageView>(R.id.writeNoRspInteractionIcon)

            sendBtn.setOnClickListener {
                val text = input.text.toString().trim()
                input.text.clear()

                val bytes: ByteArray? = try {
                    when {
                        text.startsWith("0x", true) -> text.removePrefix("0x").chunked(2)
                            .map { it.toInt(16).toByte() }.toByteArray()
                        text.startsWith("0b", true) -> text.removePrefix("0b").chunked(8)
                            .map { it.toInt(2).toByte() }.toByteArray()
                        else -> text.toByteArray(Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    icon.setImageResource(R.drawable.fail)
                    statusText.text = "Failed to send: invalid input"
                    return@setOnClickListener
                }

                if (bytes == null || bytes.isEmpty()) {
                    icon.setImageResource(R.drawable.fail)
                    statusText.text = "Failed to send: empty input"
                    return@setOnClickListener
                }

                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                characteristic.value = bytes
                ConnectionManager.writeCharacteristic(characteristic)

                icon.setImageResource(R.drawable.success)
                statusText.text = "Sent: $text"
            }

            capLayout.addView(writeNoRspBlock)
        }

        if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            val notifyBlock = inflater.inflate(R.layout.notify_block, capLayout, false)

            val statusText = notifyBlock.findViewById<TextView>(R.id.notifyStatusTextView)
            val icon = notifyBlock.findViewById<ImageView>(R.id.notifyInteractionIcon)
            val toggleSwitch = notifyBlock.findViewById<Switch>(R.id.notifyToggleSwitch)

            var notifyCount = 0

            toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                ConnectionManager.toggleNotifications(characteristic, isChecked)
                notifyCount = 0
                statusText.text = if (isChecked) "Notifications on" else "Notifications off"
                icon.setImageResource(R.drawable.success)
                if (isChecked) {
                    ConnectionManager.notificationViewMap[characteristic.uuid] = Triple(statusText, icon) { ++notifyCount }
                } else {
                    ConnectionManager.notificationViewMap.remove(characteristic.uuid)
                }
            }

            capLayout.addView(notifyBlock)

        }

        if ((props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            val indicateBlock = inflater.inflate(R.layout.indicate_block, capLayout, false)
            val statusText = indicateBlock.findViewById<TextView>(R.id.indicateStatusTextView)
            val icon = indicateBlock.findViewById<ImageView>(R.id.indicateInteractionIcon)
            val toggleSwitch = indicateBlock.findViewById<Switch>(R.id.indicateToggleSwitch)

            var lastTime = System.currentTimeMillis()

            toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                ConnectionManager.toggleIndications(characteristic, isChecked)
                lastTime = System.currentTimeMillis()
                statusText.text = if (isChecked) "Indications on" else "Indications off"
                icon.setImageResource(R.drawable.success)

                if (isChecked) {
                    ConnectionManager.indicationViewMap[characteristic.uuid] =
                        Triple(statusText, icon) {
                            val now = System.currentTimeMillis()
                            val diff = now - lastTime
                            lastTime = now
                            diff
                        }
                } else {
                    ConnectionManager.indicationViewMap.remove(characteristic.uuid)
                }
            }

            capLayout.addView(indicateBlock)
        }


        view.setOnClickListener {
            val isVisible = capLayout.isVisible
            capLayout.visibility = if (isVisible) View.GONE else View.VISIBLE
            childArrow.animate().rotation(if (isVisible) 0f else 180f).setDuration(200).start()
        }

        childArrow.rotation = if (capLayout.isVisible) 180f else 0f

        return view
    }
}
