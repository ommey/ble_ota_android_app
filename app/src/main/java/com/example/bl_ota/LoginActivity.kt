package com.example.bl_ota

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.sleep(1000)
        installSplashScreen()
        enableEdgeToEdge()

        setContentView(R.layout.activity_login)


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val loginButton = findViewById<Button>(R.id.LoginButton)
        val loginFastButton = findViewById<Button>(R.id.LoginFastButton)
        val etUsername = findViewById<EditText>(R.id.UsernameEditText)
        val etPin = findViewById<EditText>(R.id.PinEditText)

        loginFastButton.setOnClickListener{
            val loginIntent = Intent(this, OptionsActivity::class.java)
            startMqttConnection("Omar Al Ayoubi", "4321", loginIntent)
        }

        loginButton.setOnClickListener{
            val loginIntent = Intent(this, OptionsActivity::class.java)
            startMqttConnection(etUsername.text.toString(), etPin.text.toString(), loginIntent)
        }
    }

    private fun startMqttConnection(user: String, pin: String, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            MqttManager.connectAndSubscribe(user, pin,
                onConnected = {
                    runOnUiThread {
                        startActivity(intent)
                    }
                },
                onIncorrectInput = { error ->
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, error, Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "❌ MQTT error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}