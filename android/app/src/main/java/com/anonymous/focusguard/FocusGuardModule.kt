package com.anonymous.focusguard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class FocusGuardModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    companion object {
        private const val TAG = "FocusGuardModule"
        private const val MODULE_NAME = "FocusGuardNative"
        
        // SharedPreferences keys for bypass durations
        private const val PREFS_NAME = "focusguard_prefs"
        private const val KEY_LEVEL1_DURATION = "level1_duration"
        private const val KEY_LEVEL2_DURATION = "level2_duration"
        private const val DEFAULT_LEVEL1_DURATION = 5  // 5 minutes default
        private const val DEFAULT_LEVEL2_DURATION = 15 // 15 minutes default
    }
    
    private val appContext: Context = reactContext.applicationContext
    
    // Native Database Helper - accessible by services even when JS is dead
    private val dbHelper: DatabaseHelper by lazy {
        DatabaseHelper.getInstance(appContext)
    }
    
    // SharedPreferences for bypass duration settings
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    override fun getName(): String {
        return MODULE_NAME
    }
    
    // MARK: - React Native Methods
    
    @ReactMethod
    fun startAccessibilityService(promise: Promise) {
        Log.d(TAG, "Starting accessibility service")
        
        try {
            // Check if accessibility service is enabled
            if (!isAccessibilityServiceEnabled()) {
                promise.reject("ACCESSIBILITY_DISABLED", "Accessibility service is not enabled. Please enable it in settings.")
                return
            }
            
            // CRITICAL: Send ACTION_RESET_SERVICE to clear any stale state
            // This handles the "Zombie Service" problem where the service
            // remembers old state from a previous app session
            val intent = Intent(appContext, AccessibilityDetectionService::class.java).apply {
                action = AccessibilityDetectionService.ACTION_RESET_SERVICE
            }
            appContext.startService(intent)
            
            promise.resolve(true)
            Log.d(TAG, "Accessibility service started with ACTION_RESET_SERVICE")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start accessibility service", e)
            promise.reject("SERVICE_START_FAILED", "Failed to start accessibility service: ${e.message}")
        }
    }
    
    @ReactMethod
    fun stopAccessibilityService(promise: Promise) {
        Log.d(TAG, "Stopping accessibility service")
        
        try {
            val intent = Intent(appContext, AccessibilityDetectionService::class.java)
            appContext.stopService(intent)
            promise.resolve(true)
            Log.d(TAG, "Accessibility service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop accessibility service", e)
            promise.reject("SERVICE_STOP_FAILED", "Failed to stop accessibility service: ${e.message}")
        }
    }
    
    @ReactMethod
    fun startOverlayService(promise: Promise) {
        Log.d(TAG, "Starting overlay service")
        
        try {
            // Check if we have overlay permission
            if (!hasOverlayPermission()) {
                promise.reject("OVERLAY_PERMISSION_DENIED", "SYSTEM_ALERT_WINDOW permission not granted")
                return
            }
            
            // Start the overlay service
            val intent = Intent(appContext, OverlayService::class.java)
            appContext.startService(intent)
            
            promise.resolve(true)
            Log.d(TAG, "Overlay service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start overlay service", e)
            promise.reject("OVERLAY_START_FAILED", "Failed to start overlay service: ${e.message}")
        }
    }
    
    @ReactMethod
    fun stopOverlayService(promise: Promise) {
        Log.d(TAG, "Stopping overlay service")
        
        try {
            val intent = Intent(appContext, OverlayService::class.java)
            appContext.stopService(intent)
            promise.resolve(true)
            Log.d(TAG, "Overlay service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop overlay service", e)
            promise.reject("OVERLAY_STOP_FAILED", "Failed to stop overlay service: ${e.message}")
        }
    }
    
    @ReactMethod
    fun showOverlayForApp(packageName: String, promise: Promise) {
        Log.d(TAG, "Showing overlay for app: $packageName")
        
        try {
            val intent = Intent(appContext, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_OVERLAY
                putExtra(OverlayService.EXTRA_PACKAGE_NAME, packageName)
            }
            appContext.startService(intent)
            promise.resolve(true)
            Log.d(TAG, "Overlay show command sent for: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            promise.reject("SHOW_OVERLAY_FAILED", "Failed to show overlay: ${e.message}")
        }
    }
    
    @ReactMethod
    fun hideOverlay(promise: Promise) {
        Log.d(TAG, "Hiding overlay")
        
        try {
            val intent = Intent(appContext, OverlayService::class.java).apply {
                action = OverlayService.ACTION_HIDE_OVERLAY
            }
            appContext.startService(intent)
            promise.resolve(true)
            Log.d(TAG, "Overlay hide command sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay", e)
            promise.reject("HIDE_OVERLAY_FAILED", "Failed to hide overlay: ${e.message}")
        }
    }
    
    @ReactMethod
    fun hasOverlayPermission(promise: Promise) {
        promise.resolve(hasOverlayPermission())
    }
    
    @ReactMethod
    fun isAccessibilityServiceEnabled(promise: Promise) {
        promise.resolve(isAccessibilityServiceEnabled())
    }
    
    @ReactMethod
    fun openOverlayPermissionSettings(promise: Promise) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appContext.startActivity(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open overlay permission settings", e)
            promise.reject("SETTINGS_OPEN_FAILED", "Failed to open settings: ${e.message}")
        }
    }
    
    @ReactMethod
    fun openAccessibilitySettings(promise: Promise) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appContext.startActivity(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings", e)
            promise.reject("SETTINGS_OPEN_FAILED", "Failed to open settings: ${e.message}")
        }
    }
    
    // MARK: - App Fetcher Methods (Phase 2)
    
    /**
     * Get all installed launchable apps on the device
     * Filters only apps that have a launcher intent (user-facing apps)
     * Excludes our own package
     * 
     * @param promise Returns WritableArray of objects with packageName and appName
     */
    @ReactMethod
    fun getInstalledApps(promise: Promise) {
        Log.d(TAG, "Fetching installed apps")
        
        try {
            val pm = appContext.packageManager
            val myPackageName = appContext.packageName
            
            // Get all installed packages
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            
            val appsArray = Arguments.createArray()
            
            for (packageInfo in installedPackages) {
                val pkgName = packageInfo.packageName
                
                // Skip our own package
                if (pkgName == myPackageName) {
                    continue
                }
                
                // Check if app has a launch intent (is a launchable user-facing app)
                val launchIntent = pm.getLaunchIntentForPackage(pkgName)
                if (launchIntent != null) {
                    try {
                        val appInfo = packageInfo.applicationInfo ?: run {
                            Log.w(TAG, "ApplicationInfo is null for package: $pkgName")
                            throw NullPointerException("ApplicationInfo is null")
                        }
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        
                        val appMap = Arguments.createMap().apply {
                            putString("packageName", pkgName)
                            putString("appName", appName)
                        }
                        
                        appsArray.pushMap(appMap)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get info for package: $pkgName", e)
                    }
                }
            }
            
            Log.d(TAG, "Found ${appsArray.size()} launchable apps")
            promise.resolve(appsArray)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed apps", e)
            promise.reject("GET_APPS_FAILED", "Failed to get installed apps: ${e.message}")
        }
    }
    
    // MARK: - Database Methods (Phase 2)
    
    /**
     * Update or create a blocking rule for an app
     * 
     * @param packageName The package name of the app to block
     * @param level The block level (0=none, 1=nudge, 2=challenge, 3=hard block)
     * @param promise Returns true if successful
     */
    @ReactMethod
    fun updateAppRule(packageName: String, level: Int, promise: Promise) {
        Log.d(TAG, "Updating app rule: $packageName -> level $level")
        
        try {
            val success = dbHelper.updateRule(packageName, level)
            
            if (success) {
                Log.d(TAG, "Successfully updated rule for $packageName")
                promise.resolve(true)
            } else {
                Log.e(TAG, "Failed to update rule for $packageName")
                promise.reject("UPDATE_RULE_FAILED", "Failed to update rule in database")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating app rule", e)
            promise.reject("UPDATE_RULE_FAILED", "Failed to update app rule: ${e.message}")
        }
    }
    
    /**
     * Get all blocking rules from the database
     * 
     * @param promise Returns WritableArray of objects with packageName and blockLevel
     */
    @ReactMethod
    fun getAllAppRules(promise: Promise) {
        Log.d(TAG, "Fetching all app rules")
        
        try {
            val rules = dbHelper.getAllRules()
            val rulesArray = Arguments.createArray()
            
            for (rule in rules) {
                val ruleMap = Arguments.createMap().apply {
                    putString("packageName", rule["packageName"] as String)
                    putInt("blockLevel", rule["blockLevel"] as Int)
                }
                rulesArray.pushMap(ruleMap)
            }
            
            Log.d(TAG, "Retrieved ${rulesArray.size()} rules from database")
            promise.resolve(rulesArray)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app rules", e)
            promise.reject("GET_RULES_FAILED", "Failed to get app rules: ${e.message}")
        }
    }
    
    /**
     * Get the block level for a specific app
     * Used by AccessibilityDetectionService to check blocking status
     * 
     * @param packageName The package name to check
     * @param promise Returns the block level (0 if not blocked)
     */
    @ReactMethod
    fun getAppRuleLevel(packageName: String, promise: Promise) {
        Log.d(TAG, "Getting rule level for: $packageName")
        
        try {
            val level = dbHelper.getRuleLevel(packageName)
            promise.resolve(level)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get rule level for $packageName", e)
            promise.reject("GET_LEVEL_FAILED", "Failed to get rule level: ${e.message}")
        }
    }
    
    // MARK: - Helper Methods
    
    private fun hasOverlayPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(appContext)
        } else {
            // Pre-Marshmallow, permission is granted at install time
            true
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${appContext.packageName}/${AccessibilityDetectionService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        return enabledServices?.contains(serviceName) == true
    }
    
    // MARK: - Events to JavaScript
    
    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }
    
    fun sendAppDetectedEvent(packageName: String) {
        val params = Arguments.createMap().apply {
            putString("packageName", packageName)
            putDouble("timestamp", System.currentTimeMillis().toDouble())
        }
        sendEvent("onAppDetected", params)
    }
    
    fun sendOverlayShownEvent(packageName: String) {
        val params = Arguments.createMap().apply {
            putString("packageName", packageName)
            putDouble("timestamp", System.currentTimeMillis().toDouble())
        }
        sendEvent("onOverlayShown", params)
    }
    
    fun sendOverlayHiddenEvent() {
        val params = Arguments.createMap().apply {
            putDouble("timestamp", System.currentTimeMillis().toDouble())
        }
        sendEvent("onOverlayHidden", params)
    }
    
    // MARK: - Bypass Duration Methods (Phase 3)
    
    /**
     * Set bypass duration for a specific level
     * 
     * @param level The bypass level (1 or 2)
     * @param durationMinutes Duration in minutes
     * @param promise Returns true if successful
     */
    @ReactMethod
    fun setBypassDuration(level: Int, durationMinutes: Int, promise: Promise) {
        Log.d(TAG, "Setting bypass duration: level=$level, duration=$durationMinutes minutes")
        
        try {
            when (level) {
                1 -> prefs.edit().putInt(KEY_LEVEL1_DURATION, durationMinutes).apply()
                2 -> prefs.edit().putInt(KEY_LEVEL2_DURATION, durationMinutes).apply()
                else -> {
                    promise.reject("INVALID_LEVEL", "Invalid bypass level: $level. Must be 1 or 2.")
                    return
                }
            }
            
            Log.d(TAG, "Bypass duration set successfully for level $level: $durationMinutes minutes")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set bypass duration", e)
            promise.reject("SET_BYPASS_DURATION_FAILED", "Failed to set bypass duration: ${e.message}")
        }
    }
    
    /**
     * Get bypass durations for both levels
     * 
     * @param promise Returns WritableMap with level1_duration and level2_duration
     */
    @ReactMethod
    fun getBypassDurations(promise: Promise) {
        Log.d(TAG, "Getting bypass durations")
        
        try {
            val level1Duration = prefs.getInt(KEY_LEVEL1_DURATION, DEFAULT_LEVEL1_DURATION)
            val level2Duration = prefs.getInt(KEY_LEVEL2_DURATION, DEFAULT_LEVEL2_DURATION)
            
            val durationsMap = Arguments.createMap().apply {
                putInt("level1_duration", level1Duration)
                putInt("level2_duration", level2Duration)
            }
            
            Log.d(TAG, "Bypass durations retrieved: level1=$level1Duration, level2=$level2Duration")
            promise.resolve(durationsMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get bypass durations", e)
            promise.reject("GET_BYPASS_DURATIONS_FAILED", "Failed to get bypass durations: ${e.message}")
        }
    }
    
    /**
     * Get all active bypasses (non-expired)
     * 
     * @param promise Returns WritableMap of packageName -> expiryTimestamp
     */
    @ReactMethod
    fun getActiveBypasses(promise: Promise) {
        Log.d(TAG, "Getting active bypasses")
        
        try {
            val activeBypasses = dbHelper.getAllValidBypasses()
            val bypassesMap = Arguments.createMap()
            
            for ((packageName, expiryTimestamp) in activeBypasses) {
                bypassesMap.putDouble(packageName, expiryTimestamp.toDouble())
            }
            
            Log.d(TAG, "Retrieved ${activeBypasses.size} active bypasses")
            promise.resolve(bypassesMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active bypasses", e)
            promise.reject("GET_ACTIVE_BYPASSES_FAILED", "Failed to get active bypasses: ${e.message}")
        }
    }
}
