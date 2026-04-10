package com.example.classroom_silent_mode

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * AppInCallService: The Android-bindable service for call handling.
 * This service is responsible for receiving the call event from the Telecom framework.
 */
class AppInCallService : InCallService() {
    private val TAG = "AppInCallService"

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: State=${call.state}")
        
        CallManager.updateCall(call)

        // Route to the correct UI based on state
        when (call.state) {
            Call.STATE_RINGING -> {
                Log.d(TAG, "Launching IncomingCallActivity")
                val intent = Intent(this, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            Call.STATE_ACTIVE -> {
                Log.d(TAG, "Launching OngoingCallActivity")
                val intent = Intent(this, OngoingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved")
        CallManager.updateCall(null)
    }
}
