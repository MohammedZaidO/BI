package com.example.classroom_silent_mode

import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.telecom.Call
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

/**
 * OngoingCallActivity: Shown during an active call (Telecom.STATE_ACTIVE).
 */
class OngoingCallActivity : AppCompatActivity() {

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ongoing_call)

        val callerText = findViewById<TextView>(R.id.caller_id_text)
        val endBtn = findViewById<Button>(R.id.end_call_button)

        val details = CallManager.currentCall?.details
        val rawNumber = details?.handle?.schemeSpecificPart ?: "Unknown"
        callerText.text = PhoneNumberUtils.formatNumber(rawNumber, Locale.getDefault().country) ?: rawNumber

        endBtn.setOnClickListener {
            CallManager.disconnect()
            finish()
        }

        CallManager.registerCallback(callback)
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.unregisterCallback(callback)
    }
}
