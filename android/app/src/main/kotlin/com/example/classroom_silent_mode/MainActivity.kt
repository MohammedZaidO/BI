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

    companion object {
        var isClassroomModeGlobal = false
    }

    private val callReceiver = object : BroadcastReceiver() {
        private var vibrator: Vibrator? = null

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                
                if (state == TelephonyManager.EXTRA_STATE_RINGING && isClassroomModeGlobal && !incomingNumber.isNullOrEmpty()) {
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
                        } catch(e: Exception) {
                            e.printStackTrace()
                        }

                        if (isMatch) {
                            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            val pattern = longArrayOf(0, 1000, 1000)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                            } else {
                                vibrator?.vibrate(pattern, 0)
                            }
                        }
                    }
                } else if (state == TelephonyManager.EXTRA_STATE_IDLE || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    vibrator?.cancel()
                }
            }
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(callReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(callReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                val categories = Policy.PRIORITY_CATEGORY_CALLS or Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
                val callSenders = Policy.PRIORITY_SENDERS_STARRED
                val messageSenders = Policy.PRIORITY_SENDERS_STARRED
                nm.notificationPolicy = Policy(categories, callSenders, messageSenders, 0)
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                isClassroomModeGlobal = true
            }
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        }
        return true
    }

    private fun disableDnd(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
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
        try {
            unregisterReceiver(callReceiver)
        } catch(e: Exception) {}
    }
}
