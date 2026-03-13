package com.anonymous.focusguard

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * OverlayView - Full screen blocking overlay for FocusGuard
 * 
 * CRITICAL FIX: This overlay uses FLAG_NOT_FOCUSABLE to prevent the 
 * AccessibilityService from detecting our own overlay window as a 
 * "foreground app change" event, which would cause an infinite trigger loop.
 * 
 * The overlay CONSUMES all touch events (isClickable = true) to prevent
 * interaction with the blocked app underneath. FLAG_NOT_TOUCHABLE is NOT used.
 */
class OverlayView(context: Context) : FrameLayout(context) {
    
    private lateinit var blockingTextView: TextView
    
    init {
        setupView()
    }
    
    private fun setupView() {
        // Set layout parameters for full screen overlay
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        // Create the blocking text view
        blockingTextView = TextView(context).apply {
            text = "PHASE 1: APP BLOCKED"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.RED)
            setPadding(50, 100, 50, 100)
        }
        
        // Add the text view to the overlay
        addView(blockingTextView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // CRITICAL: Make overlay CONSUME all touch events
        // isClickable = true ensures touches are swallowed by the overlay
        // and do NOT pass through to the blocked app underneath
        // isFocusable = false prevents focus stealing that causes infinite loops
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
    }
    
    fun updateAppName(appName: String) {
        blockingTextView.text = "PHASE 1: $appName BLOCKED"
    }
    
    /**
     * CRITICAL WindowManager flags to prevent self-triggering loop:
     * 
     * FLAG_NOT_FOCUSABLE: Window will not receive input focus (KEEPS infinite loop fix)
     * FLAG_LAYOUT_IN_SCREEN: Place window within the entire screen
     * 
     * NOTE: FLAG_NOT_TOUCHABLE is NOT used - we WANT the overlay to consume touches
     * so the blocked app underneath cannot be interacted with.
     * The view's isClickable = true ensures touch events are swallowed.
     */
    fun getWindowManagerLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            // FLAG_NOT_FOCUSABLE prevents AccessibilityService self-trigger loop
            // NO FLAG_NOT_TOUCHABLE - overlay must consume touches to block the app
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = android.graphics.PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
    }
}
