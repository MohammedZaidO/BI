package com.example.classroom_silent_mode

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.widget.Button
import android.widget.TextView

/**
 * IncomingCallActivity: Shown when a new call is received (Telecom.STATE_RINGING).
 */
class IncomingCallActivity : Activity() {

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        val numberText = findViewById<TextView>(R.id.phone_number_text)
        val answerBtn = findViewById<Button>(R.id.answer_button)
        val rejectBtn = findViewById<Button>(R.id.reject_button)

        // Get Number
        val details = CallManager.currentCall?.details
        numberText.text = details?.handle?.schemeSpecificPart ?: "Unknown Caller"

        answerBtn.setOnClickListener {
            CallManager.answer()
            // Switch to Ongoing UI
            val intent = Intent(this, OngoingCallActivity::class.java)
            startActivity(intent)
            finish()
        }

        rejectBtn.setOnClickListener {
            CallManager.reject()
            finish()
        }

        CallManager.registerCallback(callback)
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.unregisterCallback(callback)
    }
}
