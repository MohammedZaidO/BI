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
        
        val isClassroomOn = ClassroomModeController.isPersistedEnabled(this)
        
        Log.d(TAG, "CLASSROOM_STATE_AT_CALL=${if (isClassroomOn) "ON" else "OFF"}")

        CallManager.updateCall(call)

        // Task 1 Logic: ON = Silent, OFF = Ring
        if (isClassroomOn) {
            Log.d(TAG, "RING_PATH_SELECTED=silent")
        } else {
            Log.d(TAG, "RING_PATH_SELECTED=normal")
            alertEngine.startNormalRinging()
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
