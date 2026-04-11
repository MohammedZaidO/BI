package com.example.classroom_silent_mode

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.example.classroom_silent_mode.AlertEngine
import com.example.classroom_silent_mode.CallManager
import com.example.classroom_silent_mode.ClassroomModeController

/**
 * AppInCallService: The Android-bindable service for call handling.
 */
class AppInCallService : InCallService() {
    private val TAG = "AppInCallService"
    private lateinit var alertEngine: AlertEngine

    override fun onCreate() {
        super.onCreate()
        alertEngine = AlertEngine(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        
        val details = call.details
        val incomingNumber = details.handle?.schemeSpecificPart ?: "Unknown"
        val isClassroomOn = ClassroomModeController.isPersistedEnabled(this)
        
        // Final Log Suite
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.currentInterruptionFilter
        } else -1
        
        Log.d(TAG, "DND_ACTIVE_AT_CALL=${ if (dndFilter > 1) "true" else "false" } (Filter=$dndFilter)")
        Log.d(TAG, "CLASSROOM_STATE_AT_CALL=$isClassroomOn")

        CallManager.updateCall(call)

        // Triple-Path Logic (Dialer-Owned)
        if (!isClassroomOn) {
            // Path A: Normal Ringing
            Log.d(TAG, "ALERT_PATH=normal")
            alertEngine.startNormalRinging()
        } else {
            // Path B/C: Selective Priority
            val emergencyMatcher = EmergencyMatcher(this)
            val isEmergency = emergencyMatcher.isEmergencyMatch(incomingNumber)
            
            if (isEmergency) {
                Log.d(TAG, "ALERT_PATH=emergency")
                alertEngine.startEmergencyAlert()
            } else {
                Log.d(TAG, "ALERT_PATH=silent")
                // Intentional silence (Dialer-owned)
            }
        }

        // Route to UI
        if (call.state == Call.STATE_RINGING) {
            val intent = Intent(this, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved")
        alertEngine.stopAllAlerts()
        CallManager.updateCall(null)
    }
}
