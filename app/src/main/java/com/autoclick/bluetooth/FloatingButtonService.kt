package com.autoclick.bluetooth

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Gravity
import android.view.KeyEvent
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
    private var closeButtonView: View? = null
    private var button1Params: WindowManager.LayoutParams? = null
    private var button2Params: WindowManager.LayoutParams? = null
    private var closeButtonParams: WindowManager.LayoutParams? = null
    
    private var selectionMode = false
    private var selectingButtonNumber = 0
    
    private val handler = Handler(Looper.getMainLooper())
    
    // Media session for Bluetooth button detection
    private var mediaSession: MediaSessionCompat? = null
    private var lastClickTime = 0L
    private var clickCount = 0
    private var pendingClickRunnable: Runnable? = null
    
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
        
        // Setup media session for Bluetooth buttons
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "BluetoothAutoClick").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                        handleMediaButton()
                        return true
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
                
                override fun onPlay() {
                    handleMediaButton()
                }
                
                override fun onPause() {
                    handleMediaButton()
                }
            })
            
            // Set playback state to make session active
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .build()
            )
            
            isActive = true
        }
        
        // Request audio focus to receive media button events
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.requestAudioFocus(
            { /* focus change listener */ },
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
    }
    
    private fun handleMediaButton() {
        val currentTime = System.currentTimeMillis()
        
        // Cancel pending action
        pendingClickRunnable?.let { handler.removeCallbacks(it) }
        
        if (currentTime - lastClickTime < 400) {
            clickCount++
        } else {
            clickCount = 1
        }
        
        lastClickTime = currentTime
        
        // Schedule action after timeout
        pendingClickRunnable = Runnable {
            if (clickCount >= 2) {
                triggerButton(2)
            } else {
                triggerButton(1)
            }
            clickCount = 0
        }
        
        handler.postDelayed(pendingClickRunnable!!, 400)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        
        selectionMode = intent?.getBooleanExtra("selection_mode", false) ?: false
        selectingButtonNumber = intent?.getIntExtra("button_number", 0) ?: 0
        
        if (button1View == null) {
            createFloatingButtons()
        }
        
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, FloatingButtonService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
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
            .addAction(android.R.drawable.ic_delete, "إيقاف", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingButtons() {
        val prefs = getSharedPreferences("autoclick_prefs", Context.MODE_PRIVATE)
        
        // Create Button 1
        button1View = createButton(1)
        button1Params = createLayoutParams(prefs.getInt("float_x1", 50), prefs.getInt("float_y1", 200))
        windowManager.addView(button1View, button1Params)
        
        // Create Button 2
        button2View = createButton(2)
        button2Params = createLayoutParams(prefs.getInt("float_x2", 50), prefs.getInt("float_y2", 350))
        windowManager.addView(button2View, button2Params)
        
        // Create Close Button (X)
        closeButtonView = createCloseButton()
        closeButtonParams = createLayoutParams(prefs.getInt("float_close_x", 50), prefs.getInt("float_close_y", 500))
        windowManager.addView(closeButtonView, closeButtonParams)
        
        setupTouchListeners()
    }

    private fun createButton(number: Int): View {
        val view = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        val textView = view.findViewById<TextView>(R.id.buttonText)
        textView.text = number.toString()
        textView.textSize = 18f
        
        view.findViewById<View>(R.id.buttonBackground).setBackgroundResource(
            if (number == 1) R.drawable.floating_button_1_bg else R.drawable.floating_button_2_bg
        )
        
        return view
    }
    
    private fun createCloseButton(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        val textView = view.findViewById<TextView>(R.id.buttonText)
        textView.text = "✕"
        textView.textSize = 16f
        
        view.findViewById<View>(R.id.buttonBackground).setBackgroundResource(R.drawable.floating_button_close_bg)
        
        view.setOnClickListener {
            stopSelf()
        }
        
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
        setupDragListener(closeButtonView, closeButtonParams, 0)
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
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        saveButtonPosition(buttonNumber, params?.x ?: 0, params?.y ?: 0)
                    } else if (System.currentTimeMillis() - clickStartTime < 200) {
                        if (buttonNumber == 0) {
                            // Close button clicked
                            stopSelf()
                        } else {
                            onButtonClicked(buttonNumber, params?.x ?: 0, params?.y ?: 0)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun saveButtonPosition(buttonNumber: Int, x: Int, y: Int) {
        val prefs = getSharedPreferences("autoclick_prefs", Context.MODE_PRIVATE)
        val key = if (buttonNumber == 0) "float_close" else "float"
        prefs.edit().apply {
            putInt("${key}_x${if (buttonNumber == 0) "" else buttonNumber}", x)
            putInt("${key}_y${if (buttonNumber == 0) "" else buttonNumber}", y)
            apply()
        }
    }

    private fun onButtonClicked(buttonNumber: Int, screenX: Int, screenY: Int) {
        if (selectionMode && selectingButtonNumber == buttonNumber) {
            val prefs = getSharedPreferences("autoclick_prefs", Context.MODE_PRIVATE)
            
            // Save the button's current position as the click target
            val clickX = screenX + 25
            val clickY = screenY + 25
            
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
            triggerButton(buttonNumber)
        }
    }

    fun triggerButton(buttonNumber: Int) {
        val prefs = getSharedPreferences("autoclick_prefs", Context.MODE_PRIVATE)
        val x = prefs.getInt("button${buttonNumber}_x", -1)
        val y = prefs.getInt("button${buttonNumber}_y", -1)
        
        if (x >= 0 && y >= 0) {
            AutoClickAccessibilityService.instance?.performClick(x, y)
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
        
        mediaSession?.release()
        mediaSession = null
        
        try {
            unregisterReceiver(selectionModeReceiver)
            unregisterReceiver(clickReceiver)
        } catch (e: Exception) {}
        
        try {
            button1View?.let { windowManager.removeView(it) }
            button2View?.let { windowManager.removeView(it) }
            closeButtonView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {}
    }
}
