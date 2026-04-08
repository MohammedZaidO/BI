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
     * 1. Normalize both numbers (strip non-digits, handle E.164 if possible).
     * 2. Compare exact matches.
     * 3. Fallback to suffix matching (last 7 digits) for international variability.
     */
    fun isEmergencyMatch(incomingNumber: String?): Boolean {
        if (incomingNumber.isNullOrBlank()) {
            Log.d(TAG, "Incoming number is null or blank. Match: FALSE")
            return false
        }

        val normalizedIncoming = normalize(incomingNumber)
        val savedContacts = getSavedEmergencyContacts()

        Log.d(TAG, "Matching Incoming: $incomingNumber (Normalized: $normalizedIncoming)")

        for (contact in savedContacts) {
            val normalizedSaved = normalize(contact)
            
            // 1. Precise Match using Android Utils
            if (PhoneNumberUtils.compare(context, incomingNumber, contact)) {
                Log.d(TAG, "MATCH FOUND (Native Utils): $contact")
                return true
            }

            // 2. Suffix Fallback (Last 7 digits)
            if (normalizedIncoming.length >= 7 && normalizedSaved.length >= 7) {
                if (normalizedIncoming.takeLast(7) == normalizedSaved.takeLast(7)) {
                    Log.d(TAG, "MATCH FOUND (Suffix Fallback): $contact")
                    return true
                }
            }
        }

        Log.d(TAG, "No match found for: $incomingNumber")
        return false
    }

    private fun normalize(number: String): String {
        // Strip everything except digits
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
