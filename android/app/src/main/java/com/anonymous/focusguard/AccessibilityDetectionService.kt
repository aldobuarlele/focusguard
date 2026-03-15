package com.anonymous.focusguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
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
        
        // Action for granting bypass from OverlayService
        const val ACTION_GRANT_BYPASS = "com.anonymous.focusguard.GRANT_BYPASS"
        const val EXTRA_BYPASS_PACKAGE = "BYPASS_PACKAGE"
        const val EXTRA_BYPASS_DURATION = "BYPASS_DURATION"
        
        // Action for going home (triggered from OverlayService)
        const val ACTION_GO_HOME = "com.anonymous.focusguard.GO_HOME"
        
        // Notification constants
        private const val NOTIFICATION_CHANNEL_ID = "bypass_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Active Bypasses"
        
        // SharedPreferences constants (same as FocusGuardModule)
        private const val PREFS_NAME = "focusguard_prefs"
        private const val KEY_LEVEL1_DURATION = "level1_duration"
        private const val KEY_LEVEL2_DURATION = "level2_duration"
        private const val DEFAULT_LEVEL1_DURATION = 5  // 5 minutes default
        private const val DEFAULT_LEVEL2_DURATION = 15 // 15 minutes default
        
        // Cleanup receiver action
        const val ACTION_CLEANUP_NOTIFICATION = "com.anonymous.focusguard.CLEANUP_NOTIFICATION"
        const val EXTRA_NOTIFICATION_ID = "NOTIFICATION_ID"
        const val EXTRA_CLEANUP_PACKAGE = "CLEANUP_PACKAGE"
        
        // PHASE 4: Settings Interceptor Constants
        private const val MAX_TRAVERSAL_DEPTH = 5
    }
    
    // Settings packages that could be used to uninstall/disable FocusGuard
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.miui.securitycenter",
        "com.coloros.securityguard",
        "com.oneplus.security",
        "com.samsung.android.lool"
    )
    
    // Dangerous patterns - English + Indonesian (Localized)
    // NO "security" or "administrator" to avoid false positives (e.g., fingerprint setup)
    private val DANGEROUS_PATTERNS = listOf(
        "focusguard",
        "device admin", "admin perangkat",
        "deactivate", "nonaktifkan",
        "disable", 
        "uninstall", "hapus", "copot",
        "force stop", "paksa berhenti"
    )
    
    private var currentForegroundApp: String? = null
    
    // PHASE 4 FIX: State variable to prevent intent spamming in danger zone
    private var isCurrentlyInDangerZone = false
    
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
        
        // CRITICAL: Reset state on service (re)start to force re-evaluation
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
        
        // Create notification channel for bypass notifications
        createNotificationChannel()
        
        // CRITICAL SELF-HEALING: Immediately check current foreground app on service restart
        // This fixes the bug where overlay doesn't re-appear after app is swiped from recents
        performSelfHealingCheck()
    }
    
    /**
     * SELF-HEALING CHECK: Immediately detect current foreground app on service restart
     * 
     * When FocusGuard is swiped from recents and Android restarts AccessibilityService,
     * the in-memory state is cleared. This method checks what app the user is currently
     * looking at and re-triggers blocking if needed.
     */
    private fun performSelfHealingCheck() {
        try {
            // Get the current active window's package name
            val currentWindowPackage = rootInActiveWindow?.packageName?.toString()
            
            if (currentWindowPackage != null) {
                Log.d(TAG, "SELF-HEALING: Detected current foreground app: $currentWindowPackage")
                
                // Use the existing state machine to evaluate and potentially block
                // Pass TYPE_WINDOWS_CHANGED (0) since we're detecting window state
                handleForegroundAppChange(currentWindowPackage, AccessibilityEvent.TYPE_WINDOWS_CHANGED)
            } else {
                Log.d(TAG, "SELF-HEALING: No active window detected (home screen or lockscreen)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SELF-HEALING: Failed to check current foreground app", e)
        }
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

        // 1. EARLY RETURN (MODIFIED FOR SETTINGS)
        if (trueForegroundPackage == currentForegroundApp) {
            // Ignore intra-app navigation FOR NORMAL APPS. Settings needs constant monitoring.
            if (!SETTINGS_PACKAGES.contains(trueForegroundPackage)) {
                return
            }
        }

        // 2. HYBRID BYPASS CHECK (MUST HAPPEN BEFORE INTERCEPTION)
        val expiry = bypassCache[trueForegroundPackage] ?: 0L
        if (System.currentTimeMillis() < expiry) {
            // Bypass is active! Let them access anything, even dangerous settings.
            if (currentForegroundApp != trueForegroundPackage || isCurrentlyInDangerZone) {
                hideOverlayDirectly()
                currentForegroundApp = trueForegroundPackage
                isCurrentlyInDangerZone = false
            }
            return
        }

        // 3. SMART SETTINGS INTERCEPTOR
        if (SETTINGS_PACKAGES.contains(trueForegroundPackage)) {
            if (checkSettingsInterception(rootInActiveWindow)) {
                if (!isCurrentlyInDangerZone || currentForegroundApp != trueForegroundPackage) {
                    Log.d(TAG, "DANGEROUS SETTINGS SCREEN DETECTED!")
                    triggerOverlayDirectly(trueForegroundPackage!!, 2) // Level 2
                    isCurrentlyInDangerZone = true
                }
                currentForegroundApp = trueForegroundPackage
                return // Blocked! Stop here.
            } else {
                isCurrentlyInDangerZone = false // Safe settings screen
            }
        } else {
            isCurrentlyInDangerZone = false
        }

        // 4. NORMAL DATABASE QUERY (AppRules check)
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
     * PHASE 4: SMART SETTINGS INTERCEPTOR
     * 
     * Uses Depth-Limited BFS to traverse the accessibility node tree and detect
     * dangerous settings screens (e.g., Device Admin, Uninstall, Force Stop).
     * 
     * Algorithm: Breadth-First Search with MAX_TRAVERSAL_DEPTH = 5
     * Checks: node.text and node.contentDescription against DANGEROUS_PATTERNS
     * Localization: Supports English + Indonesian
     * 
     * @param rootNode The root AccessibilityNodeInfo from rootInActiveWindow
     * @return true if dangerous settings screen detected, false otherwise
     */
    private fun checkSettingsInterception(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        
        try {
            // BFS Queue: Pair of (node, currentDepth)
            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            queue.add(Pair(rootNode, 0))
            
            while (queue.isNotEmpty()) {
                val (node, depth) = queue.poll() ?: continue
                
                // Depth limit reached - stop traversing deeper
                if (depth > MAX_TRAVERSAL_DEPTH) continue
                
                // Check node.text for dangerous patterns
                val nodeText = node.text?.toString()?.lowercase()
                if (nodeText != null) {
                    for (pattern in DANGEROUS_PATTERNS) {
                        if (nodeText.contains(pattern, ignoreCase = true)) {
                            Log.w(TAG, "INTERCEPTOR: Dangerous pattern '$pattern' found in text: $nodeText")
                            return true
                        }
                    }
                }
                
                // Check node.contentDescription for dangerous patterns
                val nodeDesc = node.contentDescription?.toString()?.lowercase()
                if (nodeDesc != null) {
                    for (pattern in DANGEROUS_PATTERNS) {
                        if (nodeDesc.contains(pattern, ignoreCase = true)) {
                            Log.w(TAG, "INTERCEPTOR: Dangerous pattern '$pattern' found in contentDescription: $nodeDesc")
                            return true
                        }
                    }
                }
                
                // Add children to queue for BFS traversal
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(Pair(child, depth + 1))
                    }
                }
            }
            
            return false // No dangerous patterns found
            
        } catch (e: Exception) {
            Log.e(TAG, "INTERCEPTOR: Error during BFS traversal", e)
            return false // Fail-safe: don't block on errors
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
    
    // MARK: - Notification & Alarm Methods
    
    /**
     * Create notification channel for bypass notifications
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active bypasses with countdown timers"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $NOTIFICATION_CHANNEL_ID")
        }
    }
    
    /**
     * Get bypass duration from SharedPreferences or use default
     */
    private fun getBypassDurationForLevel(level: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (level) {
            1 -> prefs.getInt(KEY_LEVEL1_DURATION, DEFAULT_LEVEL1_DURATION)
            2 -> prefs.getInt(KEY_LEVEL2_DURATION, DEFAULT_LEVEL2_DURATION)
            else -> DEFAULT_LEVEL1_DURATION
        }
    }
    
    /**
     * Create and show ongoing notification for bypass
     * Uses setTimeoutAfter() to auto-dismiss notification (fixes zombie notification bug on Android 12+)
     */
    private fun showBypassNotification(packageName: String, durationMinutes: Int, expiryTimestamp: Long) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Calculate duration in milliseconds for setTimeoutAfter
            val durationMs = durationMinutes * 60 * 1000L
            
            // Create notification
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            } else {
                Notification.Builder(this)
            }
            
            builder.apply {
                setContentTitle("Active Bypass: $packageName")
                setContentText("Bypass expires in")
                setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                setOngoing(true)
                setAutoCancel(false)
                setWhen(expiryTimestamp)
                
                // Use chronometer countdown if API >= 24
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                }
                
                // CRITICAL FIX: Use setTimeoutAfter to auto-dismiss notification
                // This replaces unreliable AlarmManager on Android 12+ (exact alarm restrictions)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setTimeoutAfter(durationMs)
                }
                
                // Set priority for older APIs (pre-Oreo)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    setPriority(Notification.PRIORITY_LOW)
                }
            }
            
            val notificationId = packageName.hashCode()
            notificationManager.notify(notificationId, builder.build())
            Log.d(TAG, "Bypass notification shown for $packageName (ID: $notificationId) with timeout: ${durationMs}ms")
            
            // Schedule alarm to clean up notification (fallback for pre-Oreo devices)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                scheduleNotificationCleanup(packageName, notificationId, expiryTimestamp)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show bypass notification for $packageName", e)
        }
    }
    
    /**
     * Schedule alarm to clean up notification at expiry time
     */
    private fun scheduleNotificationCleanup(packageName: String, notificationId: Int, expiryTimestamp: Long) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val cleanupIntent = Intent(this, NotificationCleanupReceiver::class.java).apply {
                action = ACTION_CLEANUP_NOTIFICATION
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_CLEANUP_PACKAGE, packageName)
            }
            
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getBroadcast(
                    this,
                    notificationId,
                    cleanupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getBroadcast(
                    this,
                    notificationId,
                    cleanupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            
            // Schedule alarm at exact expiry time
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                expiryTimestamp,
                pendingIntent
            )
            
            Log.d(TAG, "Notification cleanup scheduled for $packageName at $expiryTimestamp")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule notification cleanup for $packageName", e)
        }
    }
    
    /**
     * Update ACTION_GRANT_BYPASS handler to include notification
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESET_SERVICE -> {
                Log.d(TAG, "ACTION_RESET_SERVICE received - clearing state")
                currentForegroundApp = null
                hideOverlayDirectly()
            }
            ACTION_GRANT_BYPASS -> {
                val packageName = intent.getStringExtra(EXTRA_BYPASS_PACKAGE)
                val durationMinutes = intent.getIntExtra(EXTRA_BYPASS_DURATION, 0)
                
                if (packageName != null && durationMinutes > 0) {
                    Log.d(TAG, "ACTION_GRANT_BYPASS received - pkg: $packageName, duration: $durationMinutes min")
                    
                    // Step 1: Persist bypass to database
                    databaseHelper.grantBypass(packageName, durationMinutes)
                    
                    // Step 2: Update in-memory cache for O(1) lookup
                    val expiryTimestamp = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
                    bypassCache[packageName] = expiryTimestamp
                    Log.d(TAG, "Bypass granted until: $expiryTimestamp")
                    
                    // Step 3: Show ongoing notification with countdown
                    showBypassNotification(packageName, durationMinutes, expiryTimestamp)
                    
                    // Step 4: Hide the overlay immediately
                    hideOverlayDirectly()
                    
                    // Step 5: Reset current foreground to allow re-detection with bypass
                    currentForegroundApp = null
                } else {
                    Log.w(TAG, "ACTION_GRANT_BYPASS received with invalid params - pkg: $packageName, duration: $durationMinutes")
                }
            }
            ACTION_GO_HOME -> {
                Log.d(TAG, "ACTION_GO_HOME received - navigating to home screen")
                // Use AccessibilityService's performGlobalAction to go home
                performGlobalAction(GLOBAL_ACTION_HOME)
                // Hide overlay after going home
                hideOverlayDirectly()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }
}
