package com.anonymous.focusguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.NotificationManager

/**
 * NotificationCleanupReceiver - Cleans up bypass notifications when they expire
 * 
 * This receiver is triggered by AlarmManager when a bypass expires.
 * It cancels the ongoing notification and optionally removes the package
 * from the bypass cache.
 */
class NotificationCleanupReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationCleanupReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AccessibilityDetectionService.ACTION_CLEANUP_NOTIFICATION -> {
                val notificationId = intent.getIntExtra(
                    AccessibilityDetectionService.EXTRA_NOTIFICATION_ID, 
                    -1
                )
                val packageName = intent.getStringExtra(
                    AccessibilityDetectionService.EXTRA_CLEANUP_PACKAGE
                )
                
                if (notificationId != -1 && packageName != null) {
                    Log.d(TAG, "Cleaning up notification for $packageName (ID: $notificationId)")
                    
                    // Step 1: Cancel the notification
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)
                    Log.d(TAG, "Notification cancelled for $packageName")
                    
                    // Step 2: Optionally remove from bypass cache if accessible
                    // Note: The AccessibilityDetectionService will handle cache cleanup
                    // when it detects the bypass has expired via database check
                    
                    // Step 3: Clear expired bypasses from database
                    try {
                        val dbHelper = DatabaseHelper.getInstance(context)
                        val clearedCount = dbHelper.clearExpiredBypasses()
                        Log.d(TAG, "Cleared $clearedCount expired bypasses from database")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to clear expired bypasses", e)
                    }
                } else {
                    Log.w(TAG, "Invalid cleanup intent - missing notification ID or package name")
                }
            }
            else -> {
                Log.w(TAG, "Unknown action received: ${intent.action}")
            }
        }
    }
}