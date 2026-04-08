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
    private var vibrator: Vibrator? = null
    private var originalRingerMode: Int = -1

    companion object {
        var isClassroomModeGlobal = false
    }

    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                
                if (state == TelephonyManager.EXTRA_STATE_RINGING && isClassroomModeGlobal) {
                    // Even if number is null (API 29+ restrictions), we fallback to vibrator only for matched contacts
                    // In a future update, we can add CallLog lookup if needed.
                    if (!incomingNumber.isNullOrEmpty()) {
                        val sharedPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                        val jsonStr = sharedPrefs.getString("flutter.emergency_contacts", "[]")
                        
                        if (jsonStr != null && jsonStr != "[]") {
                            val incClean = incomingNumber.replace(Regex("\\D"), "")
                            var isMatch = false
                            try {
                                val arr = org.json.JSONArray(jsonStr)
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    val savedNum = obj.getString("phoneNumber").replace(Regex("\\D"), "")
                                    if (savedNum.isNotEmpty() && (incClean.endsWith(savedNum) || savedNum.endsWith(incClean))) {
                                        isMatch = true
                                        break
                                    }
                                }
                            } catch(e: Exception) { }

                            if (isMatch) {
                                startEmergencyVibration(context)
                            }
                        }
                    }
                } else if (state == TelephonyManager.EXTRA_STATE_IDLE || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    stopVibration()
                }
            }
        }
    }

    private fun startEmergencyVibration(context: Context) {
        if (vibrator == null) {
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 1000, 1000, 1000, 1000) // Pulse pattern
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        try {
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
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
            // Save current mode to restore later
            originalRingerMode = audioManager.ringerMode
            
            // Set to TOTAL SILENCE (no vibration from system)
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Strict policy: Allow nothing through system-wise. 
                // We will handle the vibration manually in callReceiver.
                val categories = 0 // No priority categories allowed
                nm.notificationPolicy = Policy(categories, Policy.PRIORITY_SENDERS_NONE, Policy.PRIORITY_SENDERS_NONE)
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
        stopVibration()
        return true
    }

    private fun isPriorityDndEnabled(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
    }

    override fun onDestroy() {
        super.onDestroy()
        isClassroomModeGlobal = false
        stopVibration()
        try {
            unregisterReceiver(callReceiver)
        } catch(e: Exception) {}
    }
}
