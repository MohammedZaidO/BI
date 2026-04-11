package com.example.classroom_silent_mode

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log

class ClassroomModeController(private val context: Context) {
    private val TAG = "ClassroomModeController"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var originalRingerMode: Int = -1

    companion object {
        var isEnabledGlobal = false
        private const val PREFS_NAME = "FlutterSharedPreferences"
        private const val KEY_MODE = "flutter.is_classroom_mode_enabled"

        /**
         * Reads the persisted classroom mode state.
         * Note: This is part of the 'Best-Effort' detection layer.
         */
        fun isPersistedEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_MODE, false)
        }
    }

    fun enableClassroomMode(): Boolean {
        try {
            // Persist State for Dialer decision logic
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MODE, true).apply()
            
            isEnabledGlobal = true
            Log.d(TAG, "CLASSROOM_MODE_SET_ON (Logical Only)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling Classroom Mode", e)
            return false
        }
    }

    /**
     * Disables Classroom Mode: Restoring logical state.
     */
    fun disableClassroomMode(): Boolean {
        try {
            // Clear Persist State
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MODE, false).apply()
 
            isEnabledGlobal = false
            Log.d(TAG, "CLASSROOM_MODE_SET_OFF (Logical Only)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Classroom Mode", e)
            return false
        }
    }
}
