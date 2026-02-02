package com.autoclick.bluetooth

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var statusText: TextView
    private lateinit var overlayStatusText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var bluetoothStatusText: TextView
    private lateinit var button1PositionText: TextView
    private lateinit var button2PositionText: TextView
    private lateinit var startStopButton: Button

    private val OVERLAY_PERMISSION_REQUEST = 1001
    private val BLUETOOTH_PERMISSION_REQUEST = 1002
    private val AUDIO_PERMISSION_REQUEST = 1003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()

        initViews()
        updatePermissionStatus()
        updateButtonPositions()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateButtonPositions()
        updateServiceStatus()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        overlayStatusText = findViewById(R.id.overlayStatusText)
        accessibilityStatusText = findViewById(R.id.accessibilityStatusText)
        bluetoothStatusText = findViewById(R.id.bluetoothStatusText)
        button1PositionText = findViewById(R.id.button1PositionText)
        button2PositionText = findViewById(R.id.button2PositionText)
        startStopButton = findViewById(R.id.startStopButton)

        // Overlay permission button
        findViewById<Button>(R.id.overlayPermissionButton).setOnClickListener {
            requestOverlayPermission()
        }

        // Accessibility permission button
        findViewById<Button>(R.id.accessibilityPermissionButton).setOnClickListener {
            openAccessibilitySettings()
        }

        // Bluetooth permission button
        findViewById<Button>(R.id.bluetoothPermissionButton).setOnClickListener {
            requestBluetoothPermission()
        }

        // Start/Stop service button
        startStopButton.setOnClickListener {
            toggleService()
        }

        // Set position buttons
        findViewById<Button>(R.id.setButton1PositionButton).setOnClickListener {
            startPositionSelection(1)
        }

        findViewById<Button>(R.id.setButton2PositionButton).setOnClickListener {
            startPositionSelection(2)
        }

        // Enable Bluetooth Audio button
        findViewById<Button>(R.id.enableBluetoothAudioButton).setOnClickListener {
            enableBluetoothAudio()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "floating_service_channel",
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for floating button service"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun updatePermissionStatus() {
        // Overlay permission
        val hasOverlay = Settings.canDrawOverlays(this)
        overlayStatusText.text = if (hasOverlay) getString(R.string.enabled) else getString(R.string.disabled)
        overlayStatusText.setTextColor(if (hasOverlay) getColor(R.color.success) else getColor(R.color.error))

        // Accessibility permission
        val hasAccessibility = isAccessibilityServiceEnabled()
        accessibilityStatusText.text = if (hasAccessibility) getString(R.string.enabled) else getString(R.string.disabled)
        accessibilityStatusText.setTextColor(if (hasAccessibility) getColor(R.color.success) else getColor(R.color.error))

        // Bluetooth permission
        val hasBluetooth = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        bluetoothStatusText.text = if (hasBluetooth) getString(R.string.enabled) else getString(R.string.disabled)
        bluetoothStatusText.setTextColor(if (hasBluetooth) getColor(R.color.success) else getColor(R.color.error))

        // Update start button state
        startStopButton.isEnabled = hasOverlay && hasAccessibility
    }

    private fun updateServiceStatus() {
        val isRunning = FloatingButtonService.isRunning
        statusText.text = if (isRunning) getString(R.string.service_running) else getString(R.string.service_stopped)
        statusText.setTextColor(if (isRunning) getColor(R.color.success) else getColor(R.color.error))
        startStopButton.text = if (isRunning) getString(R.string.stop_service) else getString(R.string.start_service)
    }

    private fun updateButtonPositions() {
        val prefs = getSharedPreferences("autoclick_prefs", Context.MODE_PRIVATE)
        
        val x1 = prefs.getInt("button1_x", -1)
        val y1 = prefs.getInt("button1_y", -1)
        button1PositionText.text = if (x1 >= 0 && y1 >= 0) {
            getString(R.string.position_set, x1, y1)
        } else {
            getString(R.string.position_not_set)
        }

        val x2 = prefs.getInt("button2_x", -1)
        val y2 = prefs.getInt("button2_y", -1)
        button2PositionText.text = if (x2 >= 0 && y2 >= 0) {
            getString(R.string.position_set, x2, y2)
        } else {
            getString(R.string.position_not_set)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, AutoClickAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "ابحث عن 'Bluetooth AutoClick' وفعّله", Toast.LENGTH_LONG).show()
    }

    private fun requestBluetoothPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            ),
            BLUETOOTH_PERMISSION_REQUEST
        )
    }

    private fun toggleService() {
        if (FloatingButtonService.isRunning) {
            stopService(Intent(this, FloatingButtonService::class.java))
        } else {
            if (Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()) {
                val intent = Intent(this, FloatingButtonService::class.java)
                startForegroundService(intent)
            } else {
                Toast.makeText(this, "يرجى تفعيل جميع الأذونات أولاً", Toast.LENGTH_SHORT).show()
            }
        }
        updateServiceStatus()
    }

    private fun startPositionSelection(buttonNumber: Int) {
        // Store which button we're setting position for
        val prefs = getSharedPreferences("autoclick_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("selecting_button", buttonNumber).apply()
        
        Toast.makeText(this, "افتح التطبيق المطلوب واضغط على الزر الطافي لتحديد موقع النقر", Toast.LENGTH_LONG).show()
        
        // Start the floating service if not running
        if (!FloatingButtonService.isRunning && Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FloatingButtonService::class.java)
            intent.putExtra("selection_mode", true)
            intent.putExtra("button_number", buttonNumber)
            startForegroundService(intent)
        } else if (FloatingButtonService.isRunning) {
            // Send broadcast to enter selection mode
            val intent = Intent("com.autoclick.bluetooth.SELECTION_MODE")
            intent.putExtra("button_number", buttonNumber)
            sendBroadcast(intent)
        }
    }

    private fun enableBluetoothAudio() {
        try {
            // Start Bluetooth SCO for microphone
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            
            Toast.makeText(this, getString(R.string.bluetooth_audio_active), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل في تفعيل صوت البلوتوث: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            updatePermissionStatus()
        }
    }
}
