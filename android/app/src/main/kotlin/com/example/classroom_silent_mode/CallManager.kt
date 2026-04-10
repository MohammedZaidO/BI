package com.example.classroom_silent_mode

import android.telecom.Call
import android.util.Log

/**
 * CallManager: Singleton that holds the active Telecom Call object.
 * This acts as the bridge between the InCallService and the Dialer UI.
 */
object CallManager {
    private const val TAG = "CallManager"
    var currentCall: Call? = null

    fun updateCall(call: Call?) {
        currentCall = call
    }

    fun answer() {
        currentCall?.let {
            it.answer(it.details.videoState)
            Log.d(TAG, "Call Answered")
        }
    }

    fun reject() {
        currentCall?.let {
            it.reject(false, null)
            Log.d(TAG, "Call Rejected")
        }
    }

    fun disconnect() {
        currentCall?.let {
            it.disconnect()
            Log.d(TAG, "Call Disconnected")
        }
    }

    fun registerCallback(callback: Call.Callback) {
        currentCall?.registerCallback(callback)
    }

    fun unregisterCallback(callback: Call.Callback) {
        currentCall?.unregisterCallback(callback)
    }
}
