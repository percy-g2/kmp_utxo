package ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.androdevlinux.utxo.ContextProvider

/**
 * Android implementation of haptic feedback.
 * Uses the system Vibrator/VibratorManager to provide real vibration feedback.
 * 
 * Uses the app's ContextProvider to access the Android Context without
 * requiring Compose composition context.
 */
actual fun triggerHapticFeedback(style: HapticStyle) {
    try {
        val context = ContextProvider.getContext()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (style) {
                    HapticStyle.Light -> VibrationEffect.EFFECT_TICK
                    HapticStyle.Medium -> VibrationEffect.EFFECT_CLICK
                    HapticStyle.Heavy -> VibrationEffect.EFFECT_HEAVY_CLICK
                }
                it.vibrate(VibrationEffect.createPredefined(effect))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(50) // Fallback for older Android versions
            }
        }
    } catch (e: Exception) {
        // Silently fail if vibration is not available or not permitted
        // This ensures the app doesn't crash on devices without vibration
    }
}

