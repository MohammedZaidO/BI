package com.example.classroom_silent_mode

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * AlertEngine: Owns the Ringtone and Vibration path for the Dialer.
 */
class AlertEngine(private val context: Context) {
    private val TAG = "AlertEngine"
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var ringtone: Ringtone? = null

    fun startNormalRinging() {
        Log.d(TAG, "PHASE 2: Starting Normal Ringing Path")
        
        // 1. Trigger Ringtone
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
            ringtone?.play()
            Log.d(TAG, "RING_STARTED=true")
        } catch (e: Exception) {
            Log.e(TAG, "RING_STARTED=false", e)
        }

        // 2. Trigger Vibration
        try {
            val pattern = longArrayOf(0, 1000, 1000) // 1s vibrate, 1s pause
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
            Log.d(TAG, "VIBRATION_STARTED=true")
        } catch (e: Exception) {
            Log.e(TAG, "VIBRATION_STARTED=false", e)
        }
    }

    fun stopAllAlerts() {
        Log.d(TAG, "PHASE 2: Stopping all alerts")
        ringtone?.stop()
        vibrator.cancel()
    }
    
    // Final Implementation for Task 2
    fun startEmergencyAlert() {
        Log.d(TAG, "PHASE 2: Starting EMERGENCY Alert Path")
        
        // Use the same reliable path for now
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
            ringtone?.play()
            Log.d(TAG, "RING_STARTED=true")
        } catch (e: Exception) {
            Log.e(TAG, "RING_STARTED=false", e)
        }

        try {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500) // More aggressive emergency pattern
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
            Log.d(TAG, "VIBRATION_STARTED=true")
        } catch (e: Exception) {
            Log.e(TAG, "VIBRATION_STARTED=false", e)
        }
    }
}
