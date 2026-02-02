package com.autoclick.bluetooth

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingButtonService : Service() {

    companion object {
        var isRunning = false
        var instance: FloatingButtonService? = null
        
        const val ACTION_CLICK_BUTTON_1 = "com.autoclick.bluetooth.CLICK_BUTTON_1"
        const val ACTION_CLICK_BUTTON_2 = "com.autoclick.bluetooth.CLICK_BUTTON_2"
    }

    private lateinit var windowManager: WindowManager
    private var button1View: View? = null
    private var button2View: View? = null
    private var button1Params: WindowManager.LayoutParams? = null
    private var button2Params: WindowManager.LayoutParams? = null
    
    private var selectionMode = false
    private var selectingButtonNumber = 0
    
    private val handler = Handler(Looper.getMainLooper())
    
    private val selectionModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.autoclick.bluetooth.SELECTION_MODE") {
                selectingButtonNumber = intent.getIntExtra("button_number", 0)
                selectionMode = selectingButtonNumber > 0
                updateButtonsAppearance()
            }
        }
    }
    
    private val clickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_CLICK_BUTTON_1 -> triggerButton(1)
                ACTION_CLICK_BUTTON_2 -> triggerButton(2)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Register receivers
        val selectionFilter = IntentFilter("com.autoclick.bluetooth.SELECTION_MODE")
        registerReceiver(selectionModeReceiver, selectionFilter, RECEIVER_NOT_EXPORTED)
        
        val clickFilter = IntentFilter().apply {
            addAction(ACTION_CLICK_BUTTON_1)
            addAction(ACTION_CLICK_BUTTON_2)
        }
        registerReceiver(clickReceiver, clickFilter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        
        selectionMode = intent?.getBooleanExtra("selection_mode", false) ?: false
        selectingButtonNumber = intent?.getIntExtra("button_number", 0) ?: 0
        
        createFloatingButtons()
        
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "floating_service_channel")
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingButtons() {
        val prefs = getSharedPreferences("autoclick_prefs", Context.MODE_PRIVATE)
        
        // Create Button 1
        button1View = createButton(1, prefs.getInt("float_x1", 100), prefs.getInt("float_y1", 200))
        button1Params = createLayoutParams(prefs.getInt("float_x1", 100), prefs.getInt("float_y1", 200))
        windowManager.addView(button1View, button1Params)
        
        // Create Button 2
        button2View = createButton(2, prefs.getInt("float_x2", 100), prefs.getInt("float_y2", 400))
        button2Params = createLayoutParams(prefs.getInt("float_x2", 100), prefs.getInt("float_y2", 400))
        windowManager.addView(button2View, button2Params)
        
        setupTouchListeners()
    }

    private fun createButton(number: Int, x: Int, y: Int): View {
        val view = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        val textView = view.findViewById<TextView>(R.id.buttonText)
        textView.text = number.toString()
        
        // Set color based on button number
        val bgColor = if (number == 1) R.color.button_1_color else R.color.button_2_color
        view.findViewById<View>(R.id.buttonBackground).setBackgroundResource(
            if (number == 1) R.drawable.floating_button_1_bg else R.drawable.floating_button_2_bg
        )
        
        return view
    }

    private fun createLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun setupTouchListeners() {
        setupDragListener(button1View, button1Params, 1)
        setupDragListener(button2View, button2Params, 2)
    }

    private fun setupDragListener(view: View?, params: WindowManager.LayoutParams?, buttonNumber: Int) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var clickStartTime = 0L

        view?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    clickStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        params?.x = (initialX + dx).toInt()
                        params?.y = (initialY + dy).toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // Save position
                        saveButtonPosition(buttonNumber, params?.x ?: 0, params?.y ?: 0)
                    } else if (System.currentTimeMillis() - clickStartTime < 200) {
                        // It was a click
                        onButtonClicked(buttonNumber, params?.x ?: 0, params?.y ?: 0)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun saveButtonPosition(buttonNumber: Int, x: Int, y: Int) {
        val prefs = getSharedPreferences("autoclick_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("float_x$buttonNumber", x)
            putInt("float_y$buttonNumber", y)
            apply()
        }
    }

    private fun onButtonClicked(buttonNumber: Int, screenX: Int, screenY: Int) {
        if (selectionMode && selectingButtonNumber == buttonNumber) {
            // Save click position for this button
            val prefs = getSharedPreferences("autoclick_prefs", Context.MODE_PRIVATE)
            
            // Calculate the center of the button (approximate)
            val clickX = screenX + 40 // Half of button width
            val clickY = screenY + 40 // Half of button height
            
            prefs.edit().apply {
                putInt("button${buttonNumber}_x", clickX)
                putInt("button${buttonNumber}_y", clickY)
                putInt("selecting_button", 0)
                apply()
            }
            
            selectionMode = false
            selectingButtonNumber = 0
            updateButtonsAppearance()
            vibrate()
            
        } else {
            // Normal click - trigger auto-click at saved position
            triggerButton(buttonNumber)
        }
    }

    fun triggerButton(buttonNumber: Int) {
        val prefs = getSharedPreferences("autoclick_prefs", Context.MODE_PRIVATE)
        val x = prefs.getInt("button${buttonNumber}_x", -1)
        val y = prefs.getInt("button${buttonNumber}_y", -1)
        
        if (x >= 0 && y >= 0) {
            // Send click command to accessibility service
            AutoClickAccessibilityService.instance?.performClick(x, y)
            
            // Visual feedback
            highlightButton(buttonNumber)
            vibrate()
        }
    }

    private fun highlightButton(buttonNumber: Int) {
        val view = if (buttonNumber == 1) button1View else button2View
        view?.alpha = 0.5f
        handler.postDelayed({
            view?.alpha = 1.0f
        }, 200)
    }

    private fun updateButtonsAppearance() {
        // Update appearance based on selection mode
        if (selectionMode) {
            val activeView = if (selectingButtonNumber == 1) button1View else button2View
            val inactiveView = if (selectingButtonNumber == 1) button2View else button1View
            
            activeView?.scaleX = 1.3f
            activeView?.scaleY = 1.3f
            inactiveView?.alpha = 0.5f
        } else {
            button1View?.scaleX = 1.0f
            button1View?.scaleY = 1.0f
            button2View?.scaleX = 1.0f
            button2View?.scaleY = 1.0f
            button1View?.alpha = 1.0f
            button2View?.alpha = 1.0f
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        
        try {
            unregisterReceiver(selectionModeReceiver)
            unregisterReceiver(clickReceiver)
        } catch (e: Exception) {}
        
        button1View?.let { windowManager.removeView(it) }
        button2View?.let { windowManager.removeView(it) }
    }
}
