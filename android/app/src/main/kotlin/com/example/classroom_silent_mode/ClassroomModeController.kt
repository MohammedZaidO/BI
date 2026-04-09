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
            Log.e(TAG, "DND Access not granted. Cannot enable Classroom Mode.")
            return false
        }

        try {
            // Save current state for safe restoration later
            originalRingerMode = audioManager.ringerMode
            
            // 1. BASE STATE: STRICT DND (Total Silence)
            // Using NONE ensures the system handles the heavy lifting of call muting.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
            
            // 2. Auxiliary: Set Ringer to Silent for extra local enforcement
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            
            // Persist State for background Receiver
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MODE, true).apply()
            
            isEnabledGlobal = true
            Log.d(TAG, "CLASSROOM_MODE_ON: Total Silence active.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling Classroom Mode", e)
            return false
        }
    }

    /**
     * Disables Classroom Mode: Restoring Previous State.
     */
    fun disableClassroomMode(): Boolean {
        if (!notificationManager.isNotificationPolicyAccessGranted) return false
        
        try {
            // 1. Restore DND Filter
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            
            // 2. Restore Ringer Mode
            if (originalRingerMode != -1) {
                audioManager.ringerMode = originalRingerMode
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            
            // Clear Persist State
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MODE, false).apply()

            isEnabledGlobal = false
            Log.d(TAG, "CLASSROOM_MODE_OFF: Settings restored.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Classroom Mode", e)
            return false
        }
    }
}
