package com.example.classroom_silent_mode

import android.content.Intent
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
        
        Log.d(TAG, "CLASSROOM_STATE_AT_CALL=${if (isClassroomOn) "ON" else "OFF"}")

        CallManager.updateCall(call)

        // Triple-Path Logic
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
