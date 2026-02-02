package com.autoclick.bluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

class BluetoothMediaReceiver : BroadcastReceiver() {

    companion object {
        private var lastClickTime = 0L
        private var clickCount = 0
        private val handler = Handler(Looper.getMainLooper())
        private var pendingRunnable: Runnable? = null
        
        // Time window to detect double-click (in milliseconds)
        private const val DOUBLE_CLICK_TIMEOUT = 400L
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) return
        
        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            ?: return
        
        // Only respond to key down events
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return
        
        // Handle media button events
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                handleClick(context)
            }
        }
    }

    private fun handleClick(context: Context?) {
        val currentTime = System.currentTimeMillis()
        
        // Cancel any pending single-click action
        pendingRunnable?.let { handler.removeCallbacks(it) }
        
        if (currentTime - lastClickTime < DOUBLE_CLICK_TIMEOUT) {
            // This is a double-click
            clickCount++
        } else {
            // This is a new click sequence
            clickCount = 1
        }
        
        lastClickTime = currentTime
        
        // Schedule action after timeout
        pendingRunnable = Runnable {
            if (clickCount >= 2) {
                // Double click detected - trigger button 2
                triggerButton(context, 2)
            } else {
                // Single click - trigger button 1
                triggerButton(context, 1)
            }
            clickCount = 0
        }
        
        handler.postDelayed(pendingRunnable!!, DOUBLE_CLICK_TIMEOUT)
    }

    private fun triggerButton(context: Context?, buttonNumber: Int) {
        context?.let { ctx ->
            val action = if (buttonNumber == 1) {
                FloatingButtonService.ACTION_CLICK_BUTTON_1
            } else {
                FloatingButtonService.ACTION_CLICK_BUTTON_2
            }
            
            val intent = Intent(action)
            intent.setPackage(ctx.packageName)
            ctx.sendBroadcast(intent)
        }
    }
}
