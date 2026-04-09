package com.example.classroom_silent_mode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * CallReceiver: Best-effort incoming call detection.
 * Note: This is part of the 'Best-Effort' layer and may be delayed or suppressed by OEM battery/DND policies.
 * Guaranteed Alert: The ESP32 hardware alert triggered by this receiver.
 */
class CallReceiver : BroadcastReceiver() {
    private val TAG = "CallReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Verify Mode State (Best-Effort)
        if (!ClassroomModeController.isPersistedEnabled(context)) {
            return
        }

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            
            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                // Determine caller number
                var incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                
                Log.d(TAG, "Detection Triggered: State=$state | ExtractedNumber=$incomingNumber")

                // Fallback to call log if number is missing (Common on some Android versions)
                if (incomingNumber.isNullOrBlank()) {
                    incomingNumber = getMostRecentIncomingNumber(context)
                }

                handleIncomingCall(context, incomingNumber)
            } else if (state == TelephonyManager.EXTRA_STATE_IDLE || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                // Call ended or answered
                val alertEngine = AlertEngine(context)
                alertEngine.cancelAlert()
            }
        }
    }

    private fun handleIncomingCall(context: Context, incomingNumber: String?) {
        val matcher = EmergencyMatcher(context)
        val isEmergency = matcher.isEmergencyMatch(incomingNumber)

        Log.d(TAG, "Processing Call: Number=$incomingNumber | IsEmergency=$isEmergency")

        if (isEmergency) {
            val alertEngine = AlertEngine(context)
            // Trigger ALL Alert Paths (Guaranteed + Best-Effort)
            alertEngine.triggerEmergencyAlert(incomingNumber)
        }
    }

    private fun getMostRecentIncomingNumber(context: Context): String? {
        return try {
            val cursor = context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(android.provider.CallLog.Calls.NUMBER),
                null, null,
                android.provider.CallLog.Calls.DATE + " DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call log fallback", e)
            null
        }
    }
}
