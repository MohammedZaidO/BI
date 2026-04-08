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

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
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
            
            // Set Ringer to NORMAL/VIBRATE so priority calls CAN ring.
            // (System DND will still keep regular calls 100% silent)
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Configure Policy: Allow ONLY calls from Starred Contacts.
                // Repeat callers, messages, etc. - all BLOCKED.
                val categories = Policy.PRIORITY_CATEGORY_CALLS
                val callSenders = Policy.PRIORITY_SENDERS_STARRED
                val msgSenders = Policy.PRIORITY_SENDERS_STARRED
                nm.notificationPolicy = Policy(categories, callSenders, msgSenders)
                
                // Switch to Priority Mode
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
