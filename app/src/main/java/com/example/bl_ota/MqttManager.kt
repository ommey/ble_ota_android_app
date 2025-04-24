package com.example.bl_ota

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import org.eclipse.paho.client.mqttv3.MqttException

object MqttManager{
    private const val BROKER: String = "ssl://letsencrypt.otastudent.com:8883"
    private lateinit var mqttClient: MqttClient
    private var latestFirmwareId: Int? = null
    private lateinit var token: String
    private lateinit var username: String
    private val activeSubscriptions = mutableSetOf<String>()

    suspend fun connectAndSubscribe(user: String, pin: String, onConnected: () -> Unit, onIncorrectInput: (String) -> Unit, onError: (Throwable) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                username = user.replace(" ", "")
                val clientId = MqttClient.generateClientId()
                mqttClient = MqttClient(BROKER, clientId, null)

                val connOpts = MqttConnectOptions().apply {
                    isCleanSession = true
                }

                mqttClient.connect(connOpts)
                Log.d("MQTT", "✅ Connected to broker")

                safeSubscribe("login/$username") { _, message ->
                    try {
                        val json = JSONObject(String(message.payload))
                        val status = json.getString("status")

                        if (status == "success") {
                            onConnected()
                            token = json.getString("token")
                            unsubscribeFromTopic("login/$username")
                        } else {
                            onIncorrectInput(status)
                        }
                    } catch (e: Exception) {
                        onError(e)
                    }
                }

                val payload = JSONObject().apply {
                    put("username", username)
                    put("password", pin)
                }
                val message = MqttMessage(payload.toString().toByteArray()).apply {
                    qos = 1
                }
                mqttClient.publish("login", message)

            } catch (e: Exception) {
                Log.e("MQTT", "❌ Connection or subscription failed", e)
                onError(e)
            }
        }
    }

    fun retrieveAvailableFirmware(onResult: (List<CloudFile>) -> Unit, onError: (Throwable) -> Unit = {}) {
        val topic = "firmware/List/$username"
        safeSubscribe(topic) { _, message ->
            try {
                val json = JSONObject(String(message.payload))
                val firmwareArray = json.getJSONArray("files")
                val result = mutableListOf<CloudFile>()

                for (i in 0 until firmwareArray.length()) {
                    val fw = firmwareArray.getJSONObject(i)
                    val name = fw.getString("name")
                    val version = fw.getString("version")
                    val date = fw.optString("uploaded", "N/A")

                    result.add(CloudFile(name, version, date))
                }

                onResult(result)

            } catch (e: Exception) {
                onError(e)
            }
        }

        try {
            val requestPayload = JSONObject().apply {
                put("token", token)
                put("user", username)
            }
            val msg = MqttMessage(requestPayload.toString().toByteArray()).apply { qos = 1 }
            mqttClient.publish("firmware/list", msg)
            Log.d("MQTT", "📤 Requested firmware list")
        } catch (e: Exception) {
            onError(e)
        }
    }


    fun reportUpdateStatus(device: String, status: String) {
        if (this::mqttClient.isInitialized && mqttClient.isConnected) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            val json = JSONObject().apply {
                put("token", token)
                put("deviceId", device)
                put("status", status)
                put("timestamp", timestamp)
                latestFirmwareId?.let { put("firmwareId", it) }
            }
            val message = MqttMessage(json.toString().toByteArray()).apply { qos = 1 }
            try {
                mqttClient.publish("ota/status", message)
                Log.d("MQTT", "📬 OTA status sent: $json")
            } catch (e: Exception) {
                Log.e("MQTT", "❌ Failed to publish OTA status", e)
            }
        } else {
            Log.w("MQTT", "⚠️ MQTT client not connected – OTA status not sent.")
        }
    }

    fun subscribeToAvailableFirmware(startBleFirmwareTransfer: (ByteArray) -> Unit) {
        safeSubscribe("firmware/available") { _, _ ->
            Log.d("MQTT", "📣 New firmware available, sending request...")
            subscribeToFirmwareForDevice(username, startBleFirmwareTransfer)
        }
    }

    fun subscribeToFirmwareForDevice(device: String, startBleFirmwareTransfer: (ByteArray) -> Unit) {
        val topic = "firmware/data/$device"
        safeSubscribe(topic) { _, message ->
            try {
                val json = JSONObject(String(message.payload))
                val version = json.getString("version")
                val firmwareId = json.getInt("firmwareId")
                val firmwareBase64 = json.getString("file")

                latestFirmwareId = firmwareId
                startBleFirmwareTransfer(Base64.getDecoder().decode(firmwareBase64))

                unsubscribeFromTopic(topic)
            } catch (e: Exception) {
                Log.e("MQTT", "❌ Error decoding firmware message", e)
            }
        }
    }

    fun sendFirmwareRequest(device: String, requestedVersion: String? = null) {
        val requestPayload = JSONObject().apply {
            put("token", token)
            put("deviceId", device)
            requestedVersion?.let { put("version", it) }
        }
        val message = MqttMessage(requestPayload.toString().toByteArray()).apply { qos = 1 }
        mqttClient.publish("firmware/request", message)
        Log.d("MQTT", "📤 Firmware request sent")
    }

    fun logout() {
        if (::token.isInitialized && ::mqttClient.isInitialized && mqttClient.isConnected) {
            val json = JSONObject().apply { put("token", token) }
            val message = MqttMessage(json.toString().toByteArray()).apply { qos = 1 }
            mqttClient.publish("logout", message)
            Log.d("MQTT", "📤 Logout message sent: $json")
        }
    }

    fun safeSubscribe(topic: String, callback: (String, MqttMessage) -> Unit) {
        if (!activeSubscriptions.contains(topic)) {
            try{
                mqttClient.subscribe(topic, callback)
            } catch (e: MqttException){
                Log.e("MQTT", "Safe subscribe error: ${e.message}")
            }
            activeSubscriptions.add(topic)
        }
    }

    fun unsubscribeFromTopic(topic: String) {
        try {
            mqttClient.unsubscribe(topic)
            activeSubscriptions.remove(topic)
            Log.d("MQTT", "Unsubscribed from $topic")
        } catch (e: MqttException) {
            Log.e("MQTT", "Failed to unsubscribe: ${e.message}")
        }
    }

    fun disconnect() {
        if (this::mqttClient.isInitialized && mqttClient.isConnected) {
            mqttClient.disconnect()
            Log.d("MQTT", "🔌 Disconnected from broker")
        }
    }
}
