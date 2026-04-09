package com.example.classroom_silent_mode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * AlertEngine: Manages the 'Hardware-First' alerting pipeline.
 * Reliability Model:
 * 1. ESP32 Trigger: GUARANTEED (Hardware-level vibrator)
 * 2. Phone Alerts: DEVICE-DEPENDENT (Best-Effort)
 */
class AlertEngine(private val context: Context) {
    private val TAG = "AlertEngine"
    private val NOTIF_CHANNEL_ID = "emergency_priority"
    private val NOTIF_ID = 911
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var vibrator: Vibrator? = null

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Emergency Call Alerts"
            val descriptionText = "Secondary phone alert for favourite contacts. Note: Best-effort only."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIF_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 800)
                setSound(null, null) 
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Attempt to bypass DND (Device-Dependent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun triggerEmergencyAlert(incomingNumber: String?) {
        Log.d(TAG, "Triggering Alert Sequence for: $incomingNumber")

        // === 1. GUARANTEED PATH: ESP32 Hardware ===
        Log.d(TAG, "PIPELINE: Triggering ESP32 Alert (Guaranteed Path)")
        MainActivity.triggerESP32Vibration()

        // === 2. BEST-EFFORT PATH: Phone Notifications ===
        // Note: Strict DND (InterruptionFilter.NONE) will likely suppress this vibration.
        Log.d(TAG, "PIPELINE: Triggering Phone Notification (Best-Effort Path)")
        showNotification(incomingNumber ?: "Emergency Contact")

        // === 3. AUXILIARY: Manual Motor Kick ===
        Log.d(TAG, "PIPELINE: Triggering Manual Vibration Kick (Device-Dependent)")
        startManualVibration()
    }

    private fun showNotification(contactInfo: String) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, NOTIF_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notification = builder
            .setContentTitle("Emergency Call!")
            .setContentText("Incoming: $contactInfo")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_CALL)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(NOTIF_ID, notification)
    }

    fun cancelAlert() {
        notificationManager.cancel(NOTIF_ID)
        stopManualVibration()
    }

    private fun startManualVibration() {
        if (vibrator == null) {
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 800, 200, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopManualVibration() {
        vibrator?.cancel()
    }
}
