package com.anonymous.focusguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * AccessibilityDetectionService - Core detection engine for FocusGuard
 * 
 * FINAL STATE MACHINE (Phase 2.4 - TYPE_WINDOWS_CHANGED):
 * 1. Listen to TYPE_WINDOW_STATE_CHANGED and TYPE_WINDOWS_CHANGED (NO content changes)
 * 2. For TYPE_WINDOWS_CHANGED: Use rootInActiveWindow for true foreground, skip if null
 * 3. Ultra-fast O(1) noise filter: systemui, android, null, empty, self
 * 4. State persistence: Skip if trueForegroundPackage == currentForegroundApp
 * 5. Query SQLite database for block level of valid apps
 * 6. If blockLevel > 0 -> show overlay
 * 7. If blockLevel == 0 -> hide overlay
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
    
    // Hybrid Bypass Cache: ConcurrentHashMap for O(1) reads, backed by SQLite for persistence
    private val bypassCache = ConcurrentHashMap<String, Long>()
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        
        // Initialize database helper for dynamic rule lookup
        databaseHelper = DatabaseHelper.getInstance(this)
        Log.d(TAG, "Database helper initialized")
        
        // Load all valid bypasses from database into cache to survive service restarts
        loadBypassesIntoCache()
        
        // CRITICAL: Reset state on service (re)start
        currentForegroundApp = null
        Log.d(TAG, "State reset: currentForegroundApp = null")
        
        // CRITICAL: Dynamically fetch our own package name
        ourPackageName = applicationContext.packageName
        Log.d(TAG, "Our package name (dynamic): $ourPackageName")
        
        // Configure the service - Listen to STATE_CHANGED and WINDOWS_CHANGED
        val info = AccessibilityServiceInfo().apply {
            // TYPE_WINDOW_STATE_CHANGED: App launches
            // TYPE_WINDOWS_CHANGED: Window stack changes (replaces noisy CONTENT_CHANGED)
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        
        this.serviceInfo = info
        
        Log.d(TAG, "Service configured for final state machine (TYPE_WINDOWS_CHANGED)")
        Log.d(TAG, "Self-package exclusion active: $ourPackageName")
        Log.d(TAG, "Bypass cache loaded with ${bypassCache.size} entries")
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
        
        // Process TYPE_WINDOW_STATE_CHANGED and TYPE_WINDOWS_CHANGED only
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && 
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return
        }
        
        val packageName = event.packageName?.toString()
        val eventType = event.eventType
        handleForegroundAppChange(packageName, eventType)
    }
    
    /**
     * BULLETPROOF STATE MACHINE (Phase 2.4) WITH HYBRID BYPASS CACHE:
     * 
     * 1. Resolve true foreground package based on event type
     * 2. For TYPE_WINDOWS_CHANGED: Use rootInActiveWindow, skip if null
     * 3. Strict Noise Filter: Ignore system UI, empty packages, and our own app
     * 4. State Persistence: Skip if app hasn't changed (battery saver)
     * 5. HYBRID BYPASS CHECK: Check bypass cache BEFORE database query
     * 6. Execute overlay logic based on database rule level
     */
    private fun handleForegroundAppChange(eventPackageName: String?, eventType: Int) {
        // Step 1: Resolve True Foreground Package
        val trueForegroundPackage = when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> eventPackageName
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val activeWindowPackage = rootInActiveWindow?.packageName?.toString()
                if (activeWindowPackage == null) {
                    return // OS is animating. Skip this noise.
                }
                activeWindowPackage
            }
            else -> eventPackageName
        }

        // Step 2: Strict Noise Filter
        if (trueForegroundPackage.isNullOrEmpty() || 
            trueForegroundPackage == "android" || 
            trueForegroundPackage == "com.android.systemui" || 
            trueForegroundPackage == ourPackageName) {
            return
        }

        // Step 3: State Persistence (Battery Saver)
        if (trueForegroundPackage == currentForegroundApp) {
            return // App hasn't changed, do nothing
        }

        // Step 4: HYBRID BYPASS CHECK - O(1) cache lookup
        val currentTime = System.currentTimeMillis()
        val expiryTimestamp = bypassCache[trueForegroundPackage]
        if (expiryTimestamp != null && currentTime < expiryTimestamp) {
            Log.d(TAG, "Bypass active for $trueForegroundPackage, expiry: $expiryTimestamp, current: $currentTime")
            currentForegroundApp = trueForegroundPackage // Update state
            hideOverlayDirectly()
            return // Allow access, skip blocking
        }

        // Step 5: Execute Overlay Logic
        val blockLevel = databaseHelper.getRuleLevel(trueForegroundPackage)
        currentForegroundApp = trueForegroundPackage // Update state
        
        if (blockLevel > 0) {
            triggerOverlayDirectly(trueForegroundPackage, blockLevel)
        } else {
            hideOverlayDirectly()
        }
    }
    
    /**
     * Load all valid bypasses from database into memory cache
     */
    private fun loadBypassesIntoCache() {
        try {
            bypassCache.clear()
            val validBypasses = databaseHelper.getAllValidBypasses()
            bypassCache.putAll(validBypasses)
            Log.d(TAG, "Loaded ${validBypasses.size} valid bypasses into cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bypasses into cache", e)
        }
    }

    /**
     * DIRECT SERVICE-TO-SERVICE COMMUNICATION
     * AccessibilityDetectionService -> OverlayService via Intent
     * Now includes blockLevel for overlay customization
     */
    private fun triggerOverlayDirectly(packageName: String, blockLevel: Int) {
        try {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_OVERLAY
                putExtra(OverlayService.EXTRA_TARGET_APP, packageName)
                putExtra("EXTRA_BLOCK_LEVEL", blockLevel)
            }
            startService(intent)
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
