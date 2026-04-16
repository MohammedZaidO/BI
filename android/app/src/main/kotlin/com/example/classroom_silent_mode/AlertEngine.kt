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
        Log.d(TAG, "Starting standard alert path")
        
        // 1. Trigger Ringtone
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
            ringtone?.play()
            Log.d(TAG, "RING_STARTED=true")
        } catch (e: Exception) {
            Log.e(TAG, "Ringtone failed to start", e)
        }

        // 2. Trigger Vibration
        try {
            val pattern = longArrayOf(0, 1000, 1000) // Standard pattern
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
            Log.d(TAG, "VIBRATION_STARTED=true")
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed to start", e)
        }
    }

    fun stopAllAlerts() {
        Log.d(TAG, "Cleaning up all active alerts")
        ringtone?.stop()
        vibrator.cancel()
    }
    
    fun startEmergencyAlert() {
        Log.d(TAG, "Starting priority emergency alert path")
        
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
            ringtone?.play()
            Log.d(TAG, "EMERGENCY_RING_STARTED=true")
        } catch (e: Exception) {
            Log.e(TAG, "Emergency ringtone failed", e)
        }

        try {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500) // Priority pattern
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
            Log.d(TAG, "EMERGENCY_VIBRATE_STARTED=true")
        } catch (e: Exception) {
            Log.e(TAG, "Emergency vibration failed", e)
        }
    }
}
