package com.anonymous.focusguard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * FocusGuard Device Administrator Receiver
 * 
 * This receiver handles Device Admin lifecycle events. When FocusGuard is activated
 * as a Device Administrator, it gains protection against standard uninstallation.
 * 
 * The user MUST manually deactivate FocusGuard as a device admin before they can
 * uninstall it. When they try to deactivate, onDisableRequested is called and we
 * return a warning string that Android displays in a confirmation dialog.
 * 
 * This is the FIRST layer of uninstall protection. The SECOND layer will be
 * the AccessibilityService detecting when the user navigates to the 
 * Device Admin deactivation screen.
 */
class FocusGuardDeviceAdminReceiver : DeviceAdminReceiver() {
    
    companion object {
        private const val TAG = "FocusGuardDeviceAdmin"
    }
    
    /**
     * Called when the user has enabled this Device Administrator.
     * FocusGuard is now protected from standard uninstallation.
     */
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "✅ Device Admin ENABLED - FocusGuard is now protected from uninstallation")
    }
    
    /**
     * Called when the user is about to disable this Device Administrator.
     * 
     * CRITICAL: The string we return here is shown to the user in a system dialog
     * asking them to confirm the deactivation. This is our chance to warn them.
     * 
     * @return Warning message displayed in the system confirmation dialog
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.d(TAG, "⚠️ Device Admin DISABLE REQUESTED - User is attempting to deactivate")
        
        // This warning will be shown in the system dialog
        return "⚠️ WARNING: Disabling FocusGuard as Device Administrator will allow the app to be uninstalled. Your focus protection will be lost!"
    }
    
    /**
     * Called when the user has disabled this Device Administrator.
     * FocusGuard can now be uninstalled normally.
     */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "❌ Device Admin DISABLED - FocusGuard uninstall protection is now OFF")
    }
}
