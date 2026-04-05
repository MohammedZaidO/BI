package com.example.classroom_silent_mode

import android.Manifest
import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
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
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject
import androidx.annotation.RequiresApi

class MainActivity: FlutterActivity() {
    private val CHANNEL = "phone_service"
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var audioManager: AudioManager
    private val ESP32_MAC = "C0:CD:D6:83:E4:8E"

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
                            // The JSON is stored with a special Flutter prefix occasionally, but usually standard JSON string
                            // Wait, Flutter stores strings as flutter.xxx but it's just a string in SharedPrefs
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
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        try {
            if (Build.VERSION.SDK_INT >= 33) { // Android 13+
                // RECEIVER_EXPORTED = 2
                registerReceiver(callReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), 2)
            } else {
                registerReceiver(callReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
            }
        } catch (e: Exception) { }
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                // Classroom Mode = Priority DND (this is what allows emergency/starred callers to ring)
                "ensureDndAccess" -> result.success(ensureDndAccess())
                "enablePriorityDnd" -> result.success(enablePriorityDnd())
                "disableDnd" -> result.success(disableDnd())
                "isPriorityDndEnabled" -> result.success(isPriorityDndEnabled())
                else -> result.notImplemented()
            }
        }
    }

    private fun requireBtConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun isEsp32Paired(): Boolean {
        if (!bluetoothAdapter.isEnabled) return false
        if (!requireBtConnectPermission()) return false
        
        val bonded = bluetoothAdapter.bondedDevices ?: return false
        return bonded.any { it.address.equals(ESP32_MAC, ignoreCase = true) }
    }
    
    private fun connectEsp32(onOk: () -> Unit, onErr: (String, String) -> Unit) {
        // Redundant with BLE transition
    }
    
    private fun disconnectEsp32() {
        // Redundant with BLE transition
        disableDnd()
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
        
        // Important:
        // - DND Priority filter enables "allowed exceptions"
        // - We must also set the POLICY to allow calls from STARRED (emergency) contacts
        // - Also ensure ringer isn't stuck in SILENT from previous attempts
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Let DND do the filtering; keep ringer VIBRATE so allowed callers vibrate instead of ring.
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE

                // Apply a strict policy: allow calls from STARRED + repeat callers.
                val categories =
                    Policy.PRIORITY_CATEGORY_CALLS or
                    Policy.PRIORITY_CATEGORY_REPEAT_CALLERS

                val callSenders = Policy.PRIORITY_SENDERS_STARRED
                val messageSenders = Policy.PRIORITY_SENDERS_STARRED

                // Suppress visual effects (optional); 0 means don't suppress extra visuals.
                val suppressedVisualEffects = 0

                nm.notificationPolicy = Policy(categories, callSenders, messageSenders, suppressedVisualEffects)
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                isClassroomModeGlobal = true
            }
        } catch (_: Exception) {
            // Even if policy fails on some OEMs, still try to enable priority filter.
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
