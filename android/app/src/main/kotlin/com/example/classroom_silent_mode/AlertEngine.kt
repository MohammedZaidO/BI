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
            val descriptionText = "Vibrates when a favourite contact calls during classroom mode"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIF_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                // Distinct double-pulse pattern
                vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 800)
                setSound(null, null) // Silent but vibrating
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun triggerEmergencyAlert(incomingNumber: String?) {
        Log.d(TAG, "Triggering Emergency Alert for: $incomingNumber")

        // 1. PHONE SHOUT: High-priority notification
        showNotification(incomingNumber ?: "Emergency Contact")

        // 2. MANUAL MOTOR KICK (Auxiliary)
        startManualVibration()

        // 3. BLE FALLBACK: Tell Flutter/ESP32 to vibrate
        // This is handled by MainActivity via a listener
        MainActivity.triggerESP32Vibration()
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
            .setContentText("Incoming call from: $contactInfo")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_CALL)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(NOTIF_ID, notification)
        Log.d(TAG, "NOTIFICATION_SHOUT_FIRED: Emergency Alert Notification posted.")
    }

    fun cancelAlert() {
        notificationManager.cancel(NOTIF_ID)
        stopManualVibration()
        Log.d(TAG, "Emergency Alert cancelled.")
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
