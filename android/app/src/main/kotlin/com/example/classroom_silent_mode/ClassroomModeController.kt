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
    }

    fun enableClassroomMode(): Boolean {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.e(TAG, "DND Access not granted. Cannot enable Classroom Mode.")
            return false
        }

        try {
            // Save current mode to restore later
            originalRingerMode = audioManager.ringerMode
            
            // 1. SET RINGER TO VIBRATE (Base State)
            // This ensures the hardware vibrator is "Ready" for the screening alerts.
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            
            // 2. SET DND PRIORITY FILTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Block EVERYTHING else (no calls, no messages).
                // Our "Notification Shout" is a separate high-priority event.
                val categories = 0 // No categories allowed (Silence everyone)
                val callSenders = Policy.PRIORITY_SENDERS_STARRED
                val msgSenders = Policy.PRIORITY_SENDERS_STARRED
                notificationManager.notificationPolicy = Policy(categories, callSenders, msgSenders)
                
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            
            isEnabledGlobal = true
            Log.d(TAG, "CLASSROOM_MODE_ON: Ringer=Vibrate, DND=Priority(Block All)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling Classroom Mode", e)
            return false
        }
    }

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
            
            isEnabledGlobal = false
            Log.d(TAG, "CLASSROOM_MODE_OFF: Settings restored.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Classroom Mode", e)
            return false
        }
    }
}
