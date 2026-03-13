package com.anonymous.focusguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.os.Build

/**
 * AccessibilityDetectionService - Core detection engine for FocusGuard
 * 
 * SIMPLIFIED LOGIC (Emergency Fix):
 * 1. ONLY listen to TYPE_WINDOW_STATE_CHANGED (no content changed noise)
 * 2. If target app -> show overlay
 * 3. If ANY other app (including system UI, launchers) -> hide overlay
 * 4. Self-package is ignored
 */
class AccessibilityDetectionService : AccessibilityService() {
    
    companion object {
        private const val TAG = "FocusGuardAccessibility"
        
        // Action for resetting service state from FocusGuardModule
        const val ACTION_RESET_SERVICE = "com.focusguard.ACTION_RESET_SERVICE"
        
        // Phase 1: Target Settings and Chrome for testing
        private val TARGET_APPS = setOf(
            "com.android.settings",  // Android Settings
            "com.android.chrome"     // Google Chrome
        )
        
        // Map package names to display names
        private val APP_DISPLAY_NAMES = mapOf(
            "com.android.settings" to "Settings",
            "com.android.chrome" to "Chrome"
        )
    }
    
    private var currentForegroundApp: String? = null
    
    // Dynamic self-package name (fetched at runtime, not hardcoded)
    private lateinit var ourPackageName: String
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        
        // CRITICAL: Reset state on service (re)start
        currentForegroundApp = null
        Log.d(TAG, "State reset: currentForegroundApp = null")
        
        // CRITICAL: Dynamically fetch our own package name
        ourPackageName = applicationContext.packageName
        Log.d(TAG, "Our package name (dynamic): $ourPackageName")
        
        // Configure the service - ONLY TYPE_WINDOW_STATE_CHANGED
        val info = AccessibilityServiceInfo().apply {
            // CRITICAL: ONLY listen to TYPE_WINDOW_STATE_CHANGED (no content changed)
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        
        this.serviceInfo = info
        
        Log.d(TAG, "Service configured. Target apps: ${TARGET_APPS.joinToString()}")
        Log.d(TAG, "Self-package exclusion active: $ourPackageName")
    }
    
    /**
     * Handle ACTION_RESET_SERVICE intent from FocusGuardModule
     * This resets the service state when app is restarted
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESET_SERVICE) {
            Log.d(TAG, "ACTION_RESET_SERVICE received - clearing state")
            currentForegroundApp = null
            hideOverlayDirectly()
        }
        return super.onStartCommand(intent, flags, startId)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        // ONLY process TYPE_WINDOW_STATE_CHANGED - ignore everything else
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        
        val packageName = event.packageName?.toString() ?: return
        handleForegroundAppChange(packageName)
    }
    
    /**
     * BRUTALLY SIMPLE LOGIC:
     * 1. If self-package -> ignore
     * 2. If target app -> show overlay
     * 3. ANY other package -> hide overlay
     */
    private fun handleForegroundAppChange(packageName: String) {
        // 1. Ignore our own package (prevents self-trigger loop)
        if (packageName == ourPackageName) {
            Log.d(TAG, "IGNORING: Self-package detected ($packageName)")
            return
        }
        
        // Skip if same app (no actual change)
        if (packageName == currentForegroundApp) {
            return
        }
        
        Log.d(TAG, "Foreground app changed to: $packageName")
        
        // 2. Check if this is a target app -> show overlay
        if (TARGET_APPS.contains(packageName)) {
            Log.d(TAG, "TARGET APP DETECTED: $packageName")
            currentForegroundApp = packageName
            triggerOverlayDirectly(packageName)
        } else {
            // 3. ANY other app (system UI, launcher, non-target) -> hide overlay
            Log.d(TAG, "Non-target app: $packageName - Hiding overlay")
            hideOverlayDirectly()
            currentForegroundApp = null
        }
    }
    
    /**
     * DIRECT SERVICE-TO-SERVICE COMMUNICATION
     * AccessibilityDetectionService -> OverlayService via Intent
     */
    private fun triggerOverlayDirectly(packageName: String) {
        try {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_OVERLAY
                putExtra(OverlayService.EXTRA_TARGET_APP, packageName)
            }
            startService(intent)
            Log.d(TAG, "Direct Intent sent to OverlayService for: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger overlay directly", e)
        }
    }
    
    /**
     * DIRECT SERVICE-TO-SERVICE COMMUNICATION for hiding overlay
     */
    private fun hideOverlayDirectly() {
        try {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_HIDE_OVERLAY
            }
            startService(intent)
            Log.d(TAG, "Direct Intent sent to hide overlay")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay directly", e)
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility service destroyed")
    }
}
