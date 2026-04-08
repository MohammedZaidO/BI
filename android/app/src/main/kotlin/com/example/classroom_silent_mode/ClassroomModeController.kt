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

        fun isPersistedEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_MODE, false)
        }
    }

    fun enableClassroomMode(): Boolean {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.e(TAG, "DND Access not granted. Cannot enable Classroom Mode.")
            return false
        }

        try {
            originalRingerMode = audioManager.ringerMode
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val categories = 0 
                val callSenders = Policy.PRIORITY_SENDERS_STARRED
                val msgSenders = Policy.PRIORITY_SENDERS_STARRED
                notificationManager.notificationPolicy = Policy(categories, callSenders, msgSenders)
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            
            // Persist State
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MODE, true).apply()
            
            isEnabledGlobal = true
            Log.d(TAG, "CLASSROOM_MODE_PERSISTED: ON")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling Classroom Mode", e)
            return false
        }
    }

    fun disableClassroomMode(): Boolean {
        if (!notificationManager.isNotificationPolicyAccessGranted) return false
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            
            if (originalRingerMode != -1) {
                audioManager.ringerMode = originalRingerMode
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            
            // Persist State
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MODE, false).apply()

            isEnabledGlobal = false
            Log.d(TAG, "CLASSROOM_MODE_PERSISTED: OFF")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Classroom Mode", e)
            return false
        }
    }
}
