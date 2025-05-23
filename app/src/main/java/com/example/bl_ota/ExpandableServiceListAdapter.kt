package com.example.bl_ota

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible

@Suppress("DEPRECATION")
class ExpandableServiceListAdapter(
    private val context: Context,
    private val serviceData: Map<BluetoothGattService, List<BluetoothGattCharacteristic>>
) : BaseExpandableListAdapter() {

    private val serviceList = serviceData.keys.toList()

    var expandingGroup: Int? = null

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
        view.findViewById<ImageView>(R.id.expandArrow).animate()
            .rotation(if (isExpanded) 180f else 0f).setDuration(200).start()

        return view
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup?): View {
        val inflater = LayoutInflater.from(context)
        val view = convertView ?: inflater.inflate(R.layout.layout_child, parent, false)
        val characteristic = getChild(groupPosition, childPosition) as BluetoothGattCharacteristic

        val capLayout = view.findViewById<LinearLayout>(R.id.capabilityLayout)
        val transition = LayoutTransition()
        transition.enableTransitionType(LayoutTransition.CHANGING)
        capLayout.layoutTransition = transition

        val childArrow = view.findViewById<ImageView>(R.id.childArrow)

        view.findViewById<TextView>(R.id.characteristicUUIDTextView).text = characteristic.uuid.toString()

        capLayout.removeAllViews()

        val props = characteristic.properties

        if ((props and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
            val readBlock = inflater.inflate(R.layout.read_block, capLayout, false)
            val readBtn = readBlock.findViewById<Button>(R.id.readBtn)
            val statusText = readBlock.findViewById<TextView>(R.id.readStatusText)
            val icon = readBlock.findViewById<ImageView>(R.id.readInteractionIcon)

            readBtn.setOnClickListener {
                icon.setImageResource(R.drawable.cog)
                icon.rotation = 0f
                icon.animate().rotationBy(360f).setDuration(600).withEndAction {
                    icon.animate().rotationBy(360f).setDuration(600).start()
                }.start()

                statusText.text = context.getString(R.string.reading_status)
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
                } catch (_: Exception) {
                    icon.setImageResource(R.drawable.fail)
                    statusText.text = context.getString(R.string.write_invalid_input)
                    return@setOnClickListener
                }

                if (bytes == null || bytes.isEmpty()) {
                    icon.setImageResource(R.drawable.fail)
                    statusText.text = context.getString(R.string.write_empty_input)
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
                } catch (_: Exception) {
                    icon.setImageResource(R.drawable.fail)
                    statusText.text = context.getString(R.string.write_invalid_input)
                    return@setOnClickListener
                }

                if (bytes == null || bytes.isEmpty()) {
                    icon.setImageResource(R.drawable.fail)
                    statusText.text = context.getString(R.string.write_empty_input)
                    return@setOnClickListener
                }

                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                characteristic.value = bytes
                ConnectionManager.writeCharacteristic(characteristic)

                icon.setImageResource(R.drawable.success)
                statusText.text = context.getString(R.string.write_sent_status, text)
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

            var indicateCount = 0
            toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                ConnectionManager.toggleIndications(characteristic, isChecked)
                indicateCount = 0
                statusText.text = if (isChecked) "Indications on" else "Indications off"
                icon.setImageResource(R.drawable.success)
                if (isChecked) {
                    ConnectionManager.notificationViewMap[characteristic.uuid] = Triple(statusText, icon) { ++indicateCount }
                } else {
                    ConnectionManager.notificationViewMap.remove(characteristic.uuid)
                }
            }
            capLayout.addView(indicateBlock)
        }

        view.setOnClickListener {
            val isExpanded = capLayout.isVisible

            if (isExpanded) {
                // COLLAPSE
                capLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val initialHeight = capLayout.measuredHeight

                val animation = object : android.view.animation.Animation() {
                    override fun applyTransformation(interpolatedTime: Float, t: android.view.animation.Transformation) {
                        if (interpolatedTime == 1f) {
                            capLayout.visibility = View.GONE
                        } else {
                            capLayout.layoutParams.height = (initialHeight * (1 - interpolatedTime)).toInt()
                            capLayout.requestLayout()
                        }
                    }

                    override fun willChangeBounds(): Boolean = true
                }

                animation.duration = (initialHeight / capLayout.context.resources.displayMetrics.density).toLong() * 1
                capLayout.startAnimation(animation)
                childArrow.animate().rotation(0f).setDuration(500).start()
            } else {
                // EXPAND
                capLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val targetHeight = capLayout.measuredHeight

                capLayout.layoutParams.height = 0
                capLayout.visibility = View.VISIBLE

                val animation = object : android.view.animation.Animation() {
                    override fun applyTransformation(interpolatedTime: Float, t: android.view.animation.Transformation) {
                        capLayout.layoutParams.height = (targetHeight * interpolatedTime).toInt()
                        capLayout.requestLayout()
                    }

                    override fun willChangeBounds(): Boolean = true
                }

                animation.duration = (targetHeight / capLayout.context.resources.displayMetrics.density).toLong() * 1
                capLayout.startAnimation(animation)
                childArrow.animate().rotation(180f).setDuration(500).start()
            }
        }

        childArrow.rotation = if (capLayout.isVisible) 180f else 0f

        if (groupPosition == expandingGroup) {
            view.alpha = 0f
            view.translationY = -30f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(childPosition * 75L)
                .start()
        }
        return view
    }
}
