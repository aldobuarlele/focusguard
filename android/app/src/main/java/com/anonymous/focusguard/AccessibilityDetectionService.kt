package com.anonymous.focusguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityDetectionService - Core detection engine for FocusGuard
 * 
 * PURE STATE MACHINE (Phase 2.2):
 * 1. ONLY listen to TYPE_WINDOW_STATE_CHANGED
 * 2. STRICTLY IGNORE noise: systemui, android, null, empty, self
 * 3. Query SQLite database for block level of valid apps
 * 4. If blockLevel > 0 -> show overlay
 * 5. If blockLevel == 0 -> hide overlay (e.g., com.miui.home)
 * 6. Let previous valid state persist when noise events fire
 */
class AccessibilityDetectionService : AccessibilityService() {
    
    companion object {
        private const val TAG = "FocusGuardAccessibility"
        
        // Action for resetting service state from FocusGuardModule
        const val ACTION_RESET_SERVICE = "com.focusguard.ACTION_RESET_SERVICE"
    }
    
    private var currentForegroundApp: String? = null
    
    // Dynamic self-package name (fetched at runtime, not hardcoded)
    private lateinit var ourPackageName: String
    
    // Database Helper for dynamic rule lookup
    private lateinit var databaseHelper: DatabaseHelper
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        
        // Initialize database helper for dynamic rule lookup
        databaseHelper = DatabaseHelper.getInstance(this)
        Log.d(TAG, "Database helper initialized")
        
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
        
        Log.d(TAG, "Service configured for pure state machine (no debouncer)")
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
        
        val packageName = event.packageName?.toString()
        handleForegroundAppChange(packageName)
    }
    
    /**
     * PURE STATE MACHINE (Phase 2.2):
     * 
     * 1. Strict Noise Filter: Ignore system UI, empty packages, and our own app
     * 2. Process Valid Packages: Query database and show/hide overlay accordingly
     * 3. Let previous valid state persist when noise events fire
     */
    private fun handleForegroundAppChange(packageName: String?) {
        // 1. Strict Noise Filter: Ignore system UI, empty packages, and our own app
        if (packageName.isNullOrEmpty() || 
            packageName == "android" || 
            packageName == "com.android.systemui" || 
            packageName == ourPackageName) {
            Log.d(TAG, "IGNORING noise: $packageName - letting previous state persist")
            return // Do nothing. Let the previous valid state persist.
        }

        // 2. Process Valid Packages (like com.android.chrome, com.miui.home)
        val blockLevel = databaseHelper.getRuleLevel(packageName)
        Log.d(TAG, "Valid app: $packageName, blockLevel: $blockLevel")
        
        if (blockLevel > 0) {
            Log.d(TAG, "BLOCKED APP DETECTED: $packageName (Level $blockLevel)")
            currentForegroundApp = packageName
            triggerOverlayDirectly(packageName)
        } else {
            Log.d(TAG, "Non-blocked app: $packageName - Hiding overlay")
            currentForegroundApp = packageName
            hideOverlayDirectly()
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
