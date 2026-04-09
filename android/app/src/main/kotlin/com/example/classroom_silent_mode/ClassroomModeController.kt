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

    /**
     * Enables Classroom Mode: Strict System Silence.
     * Guaranteed: The system DND will mute calls on most modern Android devices.
     */
    fun enableClassroomMode(): Boolean {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.e(TAG, "DND_APPLY_SUCCESS=false (No Permission)")
            return false
        }

        try {
            originalRingerMode = audioManager.ringerMode
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                Log.d(TAG, "DND_APPLY_SUCCESS=true")
            }
            
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            
            // Persist State for background Receiver
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MODE, true).apply()
            
            isEnabledGlobal = true
            Log.d(TAG, "CLASSROOM_MODE_SET_ON")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "DND_APPLY_SUCCESS=false", e)
            return false
        }
    }

    /**
     * Disables Classroom Mode: Restoring Previous State.
     */
    fun disableClassroomMode(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            
            if (originalRingerMode != -1) {
                audioManager.ringerMode = originalRingerMode
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            
            // Clear Persist State
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MODE, false).apply()

            isEnabledGlobal = false
            Log.d(TAG, "CLASSROOM_MODE_SET_OFF")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Classroom Mode", e)
            return false
        }
    }
}
