package com.example.classroom_silent_mode
import android.util.Log
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
    private lateinit var modeController: ClassroomModeController
    private var methodChannel: MethodChannel? = null

    companion object {
        private var instance: MainActivity? = null
        
        /**
         * Triggered by AlertEngine or CallReceiver.
         * Part of the 'Guaranteed' alerting path.
         */
        fun triggerESP32Vibration() {
            instance?.runOnUiThread {
                Log.d("MainActivity", "PHASE: Passing BLE Vibration command to Flutter.")
                instance?.methodChannel?.invokeMethod("vibrateESP32", null)
            }
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        instance = this
        modeController = ClassroomModeController(this)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "ensurePermissions" -> result.success(ensurePermissions())
                "enableClassroomMode" -> result.success(modeController.enableClassroomMode())
                "disableClassroomMode" -> result.success(modeController.disableClassroomMode())
                "isClassroomModeEnabled" -> result.success(ClassroomModeController.isEnabledGlobal)
                else -> result.notImplemented()
            }
        }
    }

    /**
     * Handles permissions for the 'Best-Effort' detection layer.
     */
    private fun ensurePermissions(): Boolean {
        Log.d("MainActivity", "CLASSROOM_ENABLE_REQUESTED")
        
        // 1. DND Access (Required for Base Silence)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                Log.d("MainActivity", "DND_ACCESS_GRANTED=false")
                Log.d("MainActivity", "OPENING_DND_SETTINGS")
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
                return false
            } else {
                Log.d("MainActivity", "DND_ACCESS_GRANTED=true")
            }
        }

        // 2. Best-Effort Detection Permissions
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_PHONE_STATE)
            }
            if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_CALL_LOG)
            }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 123)
            return false
        }
        
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        modeController.disableClassroomMode()
    }
}
