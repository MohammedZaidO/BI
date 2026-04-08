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
        
        fun triggerESP32Vibration() {
            instance?.runOnUiThread {
                Log.d("MainActivity", "Forwarding BLE_ALERT to Flutter side.")
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
                "requestCallScreeningRole" -> requestCallScreeningRole(result)
                else -> result.notImplemented()
            }
        }
    }

    private fun ensurePermissions(): Boolean {
        // 1. DND Access
        if (!modeController.enableClassroomMode()) { // This also checks access
             // Handled by controller
        }

        // 2. READ_CONTACTS (Explicitly required for CallScreeningService to see phonebook)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS), 123)
                return false
            }
        }
        return true
    }

    private fun requestCallScreeningRole(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as android.app.role.RoleManager
            if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                startActivityForResult(intent, 456)
                // Result handled in onActivityResult if needed, but for now we rely on user manual verify
            }
        }
        result.success(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        modeController.disableClassroomMode()
    }
}
