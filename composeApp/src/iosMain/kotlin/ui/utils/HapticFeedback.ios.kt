package ui.utils

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/**
 * iOS implementation of haptic feedback.
 * Uses UIImpactFeedbackGenerator to provide real haptic feedback.
 */
actual fun triggerHapticFeedback(style: HapticStyle) {
    try {
        val impactStyle = when (style) {
            HapticStyle.Light -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            HapticStyle.Medium -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            HapticStyle.Heavy -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
        }
        
        val generator = UIImpactFeedbackGenerator(impactStyle)
        generator.prepare()
        generator.impactOccurred()
    } catch (e: Exception) {
        // Silently fail if haptic feedback is not available
        // This ensures the app doesn't crash on devices without haptic support
    }
}

