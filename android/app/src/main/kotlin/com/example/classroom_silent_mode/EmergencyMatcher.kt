package com.example.classroom_silent_mode

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.util.Log
import org.json.JSONArray
import java.util.Locale

class EmergencyMatcher(private val context: Context) {
    private val TAG = "EmergencyMatcher"

    /**
     * Normalizes and matches an incoming number against saved emergency contacts.
     * Logic:
     * 1. Normalize both numbers (strip non-digits for consistent comparison).
     * 2. Compare using Android's native PhoneNumberUtils for precise matching.
     * 3. Fallback to suffix matching (last 10 digits) to handle formatting variances.
     */
    fun isEmergencyMatch(incomingNumber: String?): Boolean {
        if (incomingNumber.isNullOrBlank()) {
            Log.d(TAG, "Incoming number is null or blank. Match: FALSE")
            return false
        }

        val normalizedIncoming = normalize(incomingNumber)
        val savedContacts = getSavedEmergencyContacts()

        Log.d(TAG, "RAW_CALLER_NUMBER=$incomingNumber")
        Log.d(TAG, "NORMALIZED_CALLER_NUMBER=$normalizedIncoming")

        for (contact in savedContacts) {
            val normalizedSaved = normalize(contact)
            
            // 1. Precise Match using Android Native Utils
            if (PhoneNumberUtils.compare(context, incomingNumber, contact)) {
                Log.d(TAG, "EMERGENCY_MATCH=true (Exact Native)")
                return true
            }

            // 2. Strict 10-Digit Fallback (Requested protection against false positives)
            if (normalizedIncoming.length >= 10 && normalizedSaved.length >= 10) {
                if (normalizedIncoming.takeLast(10) == normalizedSaved.takeLast(10)) {
                    Log.d(TAG, "EMERGENCY_MATCH=true (10-Digit Match)")
                    return true
                }
            } else if (normalizedIncoming == normalizedSaved && normalizedIncoming.isNotEmpty()) {
                // Handle short numbers if they match exactly after normalization
                Log.d(TAG, "EMERGENCY_MATCH=true (Exact Normalized)")
                return true
            }
        }

        Log.d(TAG, "EMERGENCY_MATCH=false")
        return false
    }

    private fun normalize(number: String): String {
        // Strip everything except digits to handle (123) 456-7890 vs +11234567890 correctly
        return number.replace(Regex("\\D"), "")
    }

    private fun getSavedEmergencyContacts(): List<String> {
        val sharedPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val jsonStr = sharedPrefs.getString("flutter.emergency_contacts", "[]") ?: "[]"
        val contacts = mutableListOf<String>()
        
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val phone = obj.optString("phoneNumber")
                if (phone.isNotBlank()) {
                    contacts.add(phone)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing saved contacts", e)
        }
        return contacts
    }
}
