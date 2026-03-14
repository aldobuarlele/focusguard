package com.anonymous.focusguard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Native SQLite Database Helper for FocusGuard
 * 
 * CRITICAL ARCHITECTURE NOTE:
 * This database MUST be managed purely in Native Kotlin (not JS) because:
 * - AccessibilityDetectionService runs when the app is killed/swiped away
 * - JS thread is dead when app is killed, so expo-sqlite would be inaccessible
 * - This native DB can be queried by AccessibilityDetectionService directly
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "DatabaseHelper"
        
        // Database Info
        const val DATABASE_NAME = "focusguard.db"
        const val DATABASE_VERSION = 2
        
        // Table: BypassRecords
        const val TABLE_BYPASS_RECORDS = "BypassRecords"
        const val COLUMN_EXPIRY_TIMESTAMP = "expiryTimestamp"
        
        // Table: AppRules
        const val TABLE_APP_RULES = "AppRules"
        const val COLUMN_PACKAGE_NAME = "packageName"
        const val COLUMN_BLOCK_LEVEL = "blockLevel"
        
        // Block Levels
        const val LEVEL_NONE = 0      // Not blocked
        const val LEVEL_NUDGE = 1     // Level 1: Awareness/Nudge popup
        const val LEVEL_CHALLENGE = 2 // Level 2: Friction/Gamification challenge
        const val LEVEL_HARD_BLOCK = 3 // Level 3: Discipline/Hard Block
        
        // Singleton instance for service access
        @Volatile
        private var INSTANCE: DatabaseHelper? = null
        
        fun getInstance(context: Context): DatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        Log.d(TAG, "Creating database tables...")
        
        // Create AppRules table
        val createAppRulesTable = """
            CREATE TABLE $TABLE_APP_RULES (
                $COLUMN_PACKAGE_NAME TEXT PRIMARY KEY,
                $COLUMN_BLOCK_LEVEL INTEGER DEFAULT 0
            )
        """.trimIndent()
        
        // Create BypassRecords table
        val createBypassRecordsTable = """
            CREATE TABLE $TABLE_BYPASS_RECORDS (
                $COLUMN_PACKAGE_NAME TEXT PRIMARY KEY,
                $COLUMN_EXPIRY_TIMESTAMP INTEGER
            )
        """.trimIndent()
        
        db.execSQL(createAppRulesTable)
        db.execSQL(createBypassRecordsTable)
        Log.d(TAG, "Database tables created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "Upgrading database from version $oldVersion to $newVersion")
        
        when (oldVersion) {
            1 -> {
                // Version 1 to 2: Add BypassRecords table
                val createBypassRecordsTable = """
                    CREATE TABLE $TABLE_BYPASS_RECORDS (
                        $COLUMN_PACKAGE_NAME TEXT PRIMARY KEY,
                        $COLUMN_EXPIRY_TIMESTAMP INTEGER
                    )
                """.trimIndent()
                db.execSQL(createBypassRecordsTable)
                Log.d(TAG, "Added BypassRecords table for version 2")
            }
            // Add more migration steps for future versions here
        }
    }

    /**
     * Update or insert a blocking rule for an app
     * @param packageName The package name of the app
     * @param level The block level (0=none, 1=nudge, 2=challenge, 3=hard block)
     * @return true if successful, false otherwise
     */
    fun updateRule(packageName: String, level: Int): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_PACKAGE_NAME, packageName)
                put(COLUMN_BLOCK_LEVEL, level)
            }
            
            // Use INSERT OR REPLACE to handle both insert and update
            val result = db.insertWithOnConflict(
                TABLE_APP_RULES,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            
            Log.d(TAG, "Updated rule for $packageName: level=$level, result=$result")
            result != -1L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update rule for $packageName", e)
            false
        }
    }

    /**
     * Get all blocking rules
     * @return List of maps containing packageName and blockLevel
     */
    fun getAllRules(): List<Map<String, Any>> {
        val rules = mutableListOf<Map<String, Any>>()
        
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_APP_RULES,
                arrayOf(COLUMN_PACKAGE_NAME, COLUMN_BLOCK_LEVEL),
                null, null, null, null, null
            )
            
            cursor.use {
                while (it.moveToNext()) {
                    val packageName = it.getString(it.getColumnIndexOrThrow(COLUMN_PACKAGE_NAME))
                    val blockLevel = it.getInt(it.getColumnIndexOrThrow(COLUMN_BLOCK_LEVEL))
                    
                    rules.add(mapOf(
                        "packageName" to packageName,
                        "blockLevel" to blockLevel
                    ))
                }
            }
            
            Log.d(TAG, "Retrieved ${rules.size} rules from database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all rules", e)
        }
        
        return rules
    }

    /**
     * Get the block level for a specific app
     * This method is optimized for quick lookup by AccessibilityDetectionService
     * @param packageName The package name to look up
     * @return The block level (0 if not found or not blocked)
     */
    fun getRuleLevel(packageName: String): Int {
        return try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_APP_RULES,
                arrayOf(COLUMN_BLOCK_LEVEL),
                "$COLUMN_PACKAGE_NAME = ?",
                arrayOf(packageName),
                null, null, null
            )
            
            cursor.use {
                if (it.moveToFirst()) {
                    val level = it.getInt(it.getColumnIndexOrThrow(COLUMN_BLOCK_LEVEL))
                    Log.d(TAG, "Rule level for $packageName: $level")
                    level
                } else {
                    Log.d(TAG, "No rule found for $packageName, returning 0")
                    LEVEL_NONE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get rule level for $packageName", e)
            LEVEL_NONE
        }
    }

    /**
     * Delete a rule for an app (set to not blocked)
     * @param packageName The package name to remove the rule for
     * @return true if successful, false otherwise
     */
    fun deleteRule(packageName: String): Boolean {
        return try {
            val db = writableDatabase
            val result = db.delete(
                TABLE_APP_RULES,
                "$COLUMN_PACKAGE_NAME = ?",
                arrayOf(packageName)
            )
            
            Log.d(TAG, "Deleted rule for $packageName, rows affected: $result")
            result > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete rule for $packageName", e)
            false
        }
    }

    /**
     * Check if a package is blocked (has any level > 0)
     * Quick helper method for AccessibilityDetectionService
     * @param packageName The package name to check
     * @return true if the app is blocked at any level
     */
    fun isBlocked(packageName: String): Boolean {
        return getRuleLevel(packageName) > LEVEL_NONE
    }

    /**
     * Grant a temporary bypass for an app
     * @param packageName The package name to grant bypass for
     * @param durationMinutes Duration of bypass in minutes
     * @return true if successful, false otherwise
     */
    fun grantBypass(packageName: String, durationMinutes: Int): Boolean {
        return try {
            val db = writableDatabase
            val expiryTimestamp = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
            
            val values = ContentValues().apply {
                put(COLUMN_PACKAGE_NAME, packageName)
                put(COLUMN_EXPIRY_TIMESTAMP, expiryTimestamp)
            }
            
            // Use INSERT OR REPLACE to handle both insert and update
            val result = db.insertWithOnConflict(
                TABLE_BYPASS_RECORDS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            
            Log.d(TAG, "Granted bypass for $packageName: duration=$durationMinutes minutes, expiry=$expiryTimestamp, result=$result")
            result != -1L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant bypass for $packageName", e)
            false
        }
    }

    /**
     * Get the expiry timestamp for a bypass record
     * @param packageName The package name to check
     * @return The expiry timestamp in milliseconds, or 0 if no valid bypass exists
     */
    fun getBypassExpiry(packageName: String): Long {
        return try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_BYPASS_RECORDS,
                arrayOf(COLUMN_EXPIRY_TIMESTAMP),
                "$COLUMN_PACKAGE_NAME = ?",
                arrayOf(packageName),
                null, null, null
            )
            
            cursor.use {
                if (it.moveToFirst()) {
                    val expiryTimestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_EXPIRY_TIMESTAMP))
                    Log.d(TAG, "Bypass expiry for $packageName: $expiryTimestamp")
                    expiryTimestamp
                } else {
                    Log.d(TAG, "No bypass found for $packageName, returning 0")
                    0L
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get bypass expiry for $packageName", e)
            0L
        }
    }

    /**
     * Clear expired bypass records from the database
     * @return Number of expired records cleared
     */
    fun clearExpiredBypasses(): Int {
        return try {
            val db = writableDatabase
            val currentTime = System.currentTimeMillis()
            val result = db.delete(
                TABLE_BYPASS_RECORDS,
                "$COLUMN_EXPIRY_TIMESTAMP < ?",
                arrayOf(currentTime.toString())
            )
            
            Log.d(TAG, "Cleared $result expired bypass records")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear expired bypasses", e)
            0
        }
    }

    /**
     * Get all valid (non-expired) bypass records
     * @return Map of packageName to expiryTimestamp for all valid bypasses
     */
    fun getAllValidBypasses(): Map<String, Long> {
        val bypasses = mutableMapOf<String, Long>()
        
        try {
            val db = readableDatabase
            val currentTime = System.currentTimeMillis()
            val cursor = db.query(
                TABLE_BYPASS_RECORDS,
                arrayOf(COLUMN_PACKAGE_NAME, COLUMN_EXPIRY_TIMESTAMP),
                "$COLUMN_EXPIRY_TIMESTAMP >= ?",
                arrayOf(currentTime.toString()),
                null, null, null
            )
            
            cursor.use {
                while (it.moveToNext()) {
                    val packageName = it.getString(it.getColumnIndexOrThrow(COLUMN_PACKAGE_NAME))
                    val expiryTimestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_EXPIRY_TIMESTAMP))
                    bypasses[packageName] = expiryTimestamp
                }
            }
            
            Log.d(TAG, "Retrieved ${bypasses.size} valid bypass records")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all valid bypasses", e)
        }
        
        return bypasses
    }
}
