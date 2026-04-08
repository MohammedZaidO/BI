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
    private lateinit var audioManager: AudioManager
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
                
                // FALLBACK: If number is null (Android 11+), try to peek at CallLog
                if (state == TelephonyManager.EXTRA_STATE_RINGING && incomingNumber.isNullOrEmpty()) {
                    incomingNumber = getMostRecentIncomingNumber(context)
                }

                if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                    if (isNumberInFavourites(context, incomingNumber)) {
                        // FORCE TOGGLE to Vibrate for Favourite
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    } else {
                        // FORCE TOGGLE to Silent for everyone else
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    }
                } else if (state == TelephonyManager.EXTRA_STATE_IDLE || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    // Reset to Classroom Default (Silent) when call ends or is answered
                    if (isClassroomModeGlobal) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    }
                }
            }
        }
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
        
        // Register Receiver with High Priority
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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) return true
        
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return false
    }
    
    private fun enablePriorityDnd(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return false
        
        try {
            originalRingerMode = audioManager.ringerMode
            
            // Classroom Default: SILENT
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Set DND to allow nothing (we handle the favourties via Force Toggle)
                nm.notificationPolicy = Policy(0, Policy.PRIORITY_SENDERS_STARRED, Policy.PRIORITY_SENDERS_STARRED)
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            isClassroomModeGlobal = true
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun disableDnd(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        
        // Restore ringer mode
        if (originalRingerMode != -1) {
            audioManager.ringerMode = originalRingerMode
        } else {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
        
        isClassroomModeGlobal = false
        return true
    }

    private fun isPriorityDndEnabled(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
    }

    override fun onDestroy() {
        super.onDestroy()
        isClassroomModeGlobal = false
    }
}
