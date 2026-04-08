package com.example.classroom_silent_mode

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CallHandlerService : CallScreeningService() {
    private val TAG = "CallHandlerService"

    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart
        
        Log.d(TAG, "Screening Call: $incomingNumber (Classroom Mode: ${ClassroomModeController.isEnabledGlobal})")

        // 1. Logic State A: Classroom Mode OFF
        if (!ClassroomModeController.isEnabledGlobal) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val matcher = EmergencyMatcher(this)
        val alertEngine = AlertEngine(this)
        val isEmergency = matcher.isEmergencyMatch(incomingNumber)

        val responseBuilder = CallResponse.Builder()
            .setDisallowCall(false) // Never reject, only silence
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)

        if (isEmergency) {
            // 2. Logic State C: Emergency Caller
            Log.d(TAG, "MATCH FOUND: Triggering Emergency Pipeline.")
            
            // Native Silence (to remove ringtone sound)
            responseBuilder.setSilenceCall(true)
            
            // Trigger Fresh Event Notification & BLE Fallback
            alertEngine.triggerEmergencyAlert(incomingNumber)
        } else {
            // 3. Logic State B: Non-Emergency Caller
            Log.d(TAG, "NO MATCH: Maintaining Silence.")
            
            // Native Silence (no ringtone, no native vibration)
            responseBuilder.setSilenceCall(true)
        }

        // Respond within 5s
        respondToCall(callDetails, responseBuilder.build())
    }
}
