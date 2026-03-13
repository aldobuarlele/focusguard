package com.anonymous.focusguard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class FocusGuardModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    companion object {
        private const val TAG = "FocusGuardModule"
        private const val MODULE_NAME = "FocusGuardNative"
    }
    
    private val appContext: Context = reactContext.applicationContext
    
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
}