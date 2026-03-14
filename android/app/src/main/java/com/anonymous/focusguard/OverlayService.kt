package com.anonymous.focusguard

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.view.WindowManager

/**
 * OverlayService - Manages the blocking overlay display
 * 
 * CRITICAL: This service receives direct Intents from AccessibilityDetectionService
 * and displays/hides the blocking overlay accordingly.
 * 
 * Defense-in-depth: Also checks for self-package to prevent accidental loops.
 */
class OverlayService : Service() {
    
    companion object {
        private const val TAG = "FocusGuardOverlay"
        
        // Action constants for direct Intent communication
        const val ACTION_SHOW_OVERLAY = "com.focusguard.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.focusguard.ACTION_HIDE_OVERLAY"
        
        // Extra key for target app package name
        const val EXTRA_TARGET_APP = "TARGET_APP"
        
        // Legacy extra key (for backward compatibility)
        const val EXTRA_PACKAGE_NAME = "package_name"
        
        // Extra key for block level (1=nudge, 2=challenge, 3=hard block)
        const val EXTRA_BLOCK_LEVEL = "EXTRA_BLOCK_LEVEL"
        
        // Map package names to display names
        private val APP_DISPLAY_NAMES = mapOf(
            "com.android.settings" to "Settings",
            "com.android.chrome" to "Chrome",
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube"
        )
    }
    
    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null
    private var isOverlayShowing = false
    private var currentBlockedApp: String? = null
    
    // Dynamic self-package name (defense-in-depth)
    private lateinit var ourPackageName: String
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService created")
        
        // CRITICAL: Dynamically fetch our own package name
        ourPackageName = applicationContext.packageName
        Log.d(TAG, "Our package name (dynamic): $ourPackageName")
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // DO NOT show any overlay on service creation
        // Only show overlay when explicitly triggered by AccessibilityService via Intent
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayService onStartCommand - action: ${intent?.action}")
        
        // If no intent or no action, just keep service running (no overlay shown)
        if (intent == null || intent.action == null) {
            Log.d(TAG, "Service started without action - keeping service alive, no overlay")
            return START_STICKY
        }
        
        when (intent.action) {
            ACTION_SHOW_OVERLAY -> {
                // Extract package name from Intent (direct from AccessibilityService)
                val targetApp = intent.getStringExtra(EXTRA_TARGET_APP) 
                    ?: intent.getStringExtra(EXTRA_PACKAGE_NAME)
                
                if (targetApp != null) {
                    // DEFENSE-IN-DEPTH: Never show overlay for our own package
                    if (targetApp == ourPackageName) {
                        Log.w(TAG, "BLOCKED: Attempted to show overlay for self-package!")
                        return START_STICKY
                    }
                    
                    // Extract block level from intent (default to 3 for hard block)
                    val blockLevel = intent.getIntExtra(EXTRA_BLOCK_LEVEL, 3)
                    Log.d(TAG, "ACTION_SHOW_OVERLAY received for: $targetApp with blockLevel: $blockLevel")
                    showOverlay(targetApp, blockLevel)
                } else {
                    Log.w(TAG, "ACTION_SHOW_OVERLAY received but no target app specified")
                }
            }
            ACTION_HIDE_OVERLAY -> {
                Log.d(TAG, "ACTION_HIDE_OVERLAY received")
                hideOverlay()
            }
            else -> {
                Log.d(TAG, "Unknown action: ${intent.action}")
            }
        }
        
        // Return START_STICKY to keep service running
        return START_STICKY
    }
    
    private fun showOverlay(packageName: String, blockLevel: Int) {
        Log.d(TAG, "Showing overlay for package: $packageName with blockLevel: $blockLevel")
        
        // If already showing for same app, don't recreate
        if (isOverlayShowing && currentBlockedApp == packageName) {
            Log.d(TAG, "Overlay already showing for $packageName, skipping")
            return
        }
        
        // Remove existing overlay if showing for different app
        if (isOverlayShowing) {
            hideOverlay()
        }
        
        runOnUiThread {
            try {
                // Get display name for the app
                val displayName = APP_DISPLAY_NAMES[packageName] ?: packageName
                
                // Read bypass durations from SharedPreferences (same as FocusGuardModule)
                val prefs = getSharedPreferences("focusguard_prefs", Context.MODE_PRIVATE)
                val level1Duration = prefs.getInt("level1_duration", 5)  // Default 5 minutes
                val level2Duration = prefs.getInt("level2_duration", 15) // Default 15 minutes
                
                Log.d(TAG, "Bypass durations from SharedPreferences: level1=$level1Duration, level2=$level2Duration")
                
                // Create and setup overlay view with block level, durations, and callbacks
                overlayView = OverlayView(this).apply {
                    showOverlay(
                        packageName = packageName,
                        blockLevel = blockLevel,
                        durationL1 = level1Duration,
                        durationL2 = level2Duration,
                        onBypassRequested = { duration ->
                            // Send GRANT_BYPASS intent to AccessibilityDetectionService
                            Log.d(TAG, "Bypass requested for $packageName, duration: $duration minutes")
                            val bypassIntent = Intent(this@OverlayService, AccessibilityDetectionService::class.java).apply {
                                action = AccessibilityDetectionService.ACTION_GRANT_BYPASS
                                putExtra(AccessibilityDetectionService.EXTRA_BYPASS_PACKAGE, packageName)
                                putExtra(AccessibilityDetectionService.EXTRA_BYPASS_DURATION, duration)
                            }
                            startService(bypassIntent)
                        },
                        onGoHome = {
                            // Send GO_HOME intent to AccessibilityDetectionService
                            Log.d(TAG, "Go Home requested")
                            val homeIntent = Intent(this@OverlayService, AccessibilityDetectionService::class.java).apply {
                                action = AccessibilityDetectionService.ACTION_GO_HOME
                            }
                            startService(homeIntent)
                        }
                    )
                }
                
                // Add overlay to window using CRITICAL non-touchable flags
                val params = overlayView!!.getWindowManagerLayoutParams()
                windowManager.addView(overlayView, params)
                
                isOverlayShowing = true
                currentBlockedApp = packageName
                
                Log.d(TAG, "✅ OVERLAY SHOWN - FOCUSGUARD: $displayName BLOCKED (Level $blockLevel) with durations: L1=$level1Duration, L2=$level2Duration")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay", e)
            }
        }
    }
    
    private fun hideOverlay() {
        Log.d(TAG, "Hiding overlay")
        
        if (!isOverlayShowing || overlayView == null) {
            Log.d(TAG, "No overlay to hide")
            return
        }
        
        runOnUiThread {
            try {
                windowManager.removeView(overlayView)
                overlayView = null
                isOverlayShowing = false
                currentBlockedApp = null
                Log.d(TAG, "✅ Overlay hidden successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide overlay", e)
            }
        }
    }
    
    private fun runOnUiThread(action: () -> Unit) {
        // Simple UI thread execution
        android.os.Handler(mainLooper).post(action)
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        // This is a started service, not bound
        return null
    }
    
    /**
     * CRITICAL FIX: Do NOT kill service when app is swiped from recents!
     * 
     * When the FocusGuard app is swiped away from recents, the app process dies BUT
     * we want this service to SURVIVE. Android will restart it due to START_STICKY.
     * 
     * DO NOT call stopSelf() here - that prevents the service from being revived!
     * DO NOT hide overlay here - AccessibilityService will re-trigger if needed.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved: App swiped from recents - SERVICE WILL SURVIVE (zombie mode)")
        // DO NOT call hideOverlay() - let AccessibilityService manage overlay state
        // DO NOT call stopSelf() - service must survive to be restarted
        super.onTaskRemoved(rootIntent)
    }
    
    override fun onDestroy() {
        Log.d(TAG, "OverlayService destroying")
        
        // Hide overlay if showing
        hideOverlay()
        
        super.onDestroy()
    }
}
