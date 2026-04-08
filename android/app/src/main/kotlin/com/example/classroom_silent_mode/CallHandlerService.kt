package com.example.classroom_silent_mode

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CallHandlerService : CallScreeningService() {
    private val TAG = "CallHandlerService"

    override fun onScreenCall(callDetails: Call.Details) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val threadName = Thread.currentThread().name
        val incomingNumber = callDetails.handle?.schemeSpecificPart ?: "UNKNOWN"
        
        Log.d(TAG, "EVIDENCE: onScreenCall entered | Time: $timestamp | Thread: $threadName")
        Log.d(TAG, "EVIDENCE: incomingNumberRaw: $incomingNumber")

        // READ PERSISTED STATE
        val isModeEnabled = ClassroomModeController.isPersistedEnabled(this)
        Log.d(TAG, "EVIDENCE: classroomMode=$isModeEnabled")

        val responseBuilder = CallResponse.Builder()

        if (isModeEnabled) {
            // DEBUG BRANCH: FORCE SILENCE EVERYTHING TO PROVE PIPELINE
            Log.d(TAG, "EVIDENCE: debugSilenceAllCallsInClassroom=true")
            
            responseBuilder
                .setDisallowCall(false) // Not blocking, just silencing
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .setSilenceCall(true)
        } else {
            // NORMAL BRANCH: PASS THROUGH
            Log.d(TAG, "EVIDENCE: debugSilenceAllCallsInClassroom=false (Mode is OFF)")
            responseBuilder
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .setSilenceCall(false)
        }

        val response = responseBuilder.build()
        // Log the variables used for the build since the builder doesn't expose them after construction
        Log.d(TAG, "EVIDENCE: response flags disallow=false reject=false skipLog=false skipNotification=false silence=$isModeEnabled")
        
        Log.d(TAG, "EVIDENCE: respondToCall executed")
        respondToCall(callDetails, response)
    }
}
