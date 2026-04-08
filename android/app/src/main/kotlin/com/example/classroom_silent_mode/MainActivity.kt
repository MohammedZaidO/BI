package com.example.classroom_silent_mode

import android.Manifest
import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.TelephonyManager

class MainActivity: FlutterActivity() {
    private val CHANNEL = "phone_service"
    private val NOTIF_CHANNEL_ID = "emergency_priority"
    private val NOTIF_ID = 911
    
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private var vibrator: Vibrator? = null
    private var originalRingerMode: Int = -1

    companion object {
        var isClassroomModeGlobal = false
    }

    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isClassroomModeGlobal) return

            if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                var incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                
                if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                    if (incomingNumber.isNullOrEmpty()) {
                        incomingNumber = getMostRecentIncomingNumber(context)
                    }

                    if (isNumberInFavourites(context, incomingNumber)) {
                        // FAVOURITE DETECTED: Notification Shout
                        showEmergencyNotification(context, incomingNumber ?: "Favourite Contact")
                        
                        // Auxiliary: Force Ringer/DND for extra reliability
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        }
                    }
                } else if (state == TelephonyManager.EXTRA_STATE_IDLE || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    // Call ended: Clear Shout & Restore Silent
                    cancelEmergencyNotification()
                    
                    if (isClassroomModeGlobal) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Emergency Call Alerts"
            val descriptionText = "Vibrates when a favourite contact calls during classroom mode"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(NOTIF_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500)
                setSound(null, null) // Silent but vibrating
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showEmergencyNotification(context: Context, contactInfo: String) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, NOTIF_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }

        val notification = builder
            .setContentTitle("Favourite Calling!")
            .setContentText("Incoming call from: $contactInfo")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(android.app.Notification.PRIORITY_MAX)
            .setCategory(android.app.Notification.CATEGORY_CALL)
            .setFullScreenIntent(null, true)
            .build()
            
        notificationManager.notify(NOTIF_ID, notification)
        
        // Manual motor kick as well
        startManualVibration(context)
    }

    private fun cancelEmergencyNotification() {
        notificationManager.cancel(NOTIF_ID)
        stopManualVibration()
    }

    private fun startManualVibration(context: Context) {
        if (vibrator == null) {
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 1000, 500, 1000, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopManualVibration() {
        vibrator?.cancel()
    }

    private fun getMostRecentIncomingNumber(context: Context): String? {
        try {
            val cursor = context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(android.provider.CallLog.Calls.NUMBER),
                null, null,
                android.provider.CallLog.Calls.DATE + " DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (e: Exception) { }
        return null
    }

    private fun isNumberInFavourites(context: Context, incomingNumber: String?): Boolean {
        if (incomingNumber.isNullOrEmpty()) return false
        val sharedPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val jsonStr = sharedPrefs.getString("flutter.emergency_contacts", "[]") ?: "[]"
        val incClean = incomingNumber.replace(Regex("\\D"), "")
        try {
            val arr = org.json.JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val savedNum = obj.getString("phoneNumber").replace(Regex("\\D"), "")
                if (savedNum.isNotEmpty() && (incClean.endsWith(savedNum) || savedNum.endsWith(incClean))) {
                    return true
                }
            }
        } catch (e: Exception) { }
        return false
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()
        
        try {
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            filter.priority = Int.MAX_VALUE
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(callReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(callReceiver, filter)
            }
        } catch (e: Exception) { }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "ensureDndAccess" -> result.success(ensureDndAccess())
                "enablePriorityDnd" -> result.success(enablePriorityDnd())
                "disableDnd" -> result.success(disableDnd())
                "isPriorityDndEnabled" -> result.success(isPriorityDndEnabled())
                else -> result.notImplemented()
            }
        }
    }
    
    private fun ensureDndAccess(): Boolean {
        if (notificationManager.isNotificationPolicyAccessGranted) return true
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return false
    }
    
    private fun enablePriorityDnd(): Boolean {
        if (!notificationManager.isNotificationPolicyAccessGranted) return false
        try {
            originalRingerMode = audioManager.ringerMode
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.notificationPolicy = Policy(0, Policy.PRIORITY_SENDERS_STARRED, Policy.PRIORITY_SENDERS_STARRED)
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            isClassroomModeGlobal = true
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun disableDnd(): Boolean {
        if (!notificationManager.isNotificationPolicyAccessGranted) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        if (originalRingerMode != -1) {
            audioManager.ringerMode = originalRingerMode
        } else {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
        isClassroomModeGlobal = false
        cancelEmergencyNotification()
        return true
    }

    private fun isPriorityDndEnabled(): Boolean {
        return notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
    }

    override fun onDestroy() {
        super.onDestroy()
        isClassroomModeGlobal = false
        cancelEmergencyNotification()
    }
}
