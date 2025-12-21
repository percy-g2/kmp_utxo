package ui.utils

/**
 * Platform-aware haptic feedback trigger.
 * 
 * Provides haptic feedback when clicks are accepted (not debounced).
 * Platform-specific implementations:
 * - Android: Real vibration using VibrationEffect
 * - iOS: Real haptic feedback using UIImpactFeedbackGenerator
 * - Desktop/Wasm: Safe no-op (does nothing, never crashes)
 * 
 * @param style The intensity/style of haptic feedback (default: Light)
 */
expect fun triggerHapticFeedback(style: HapticStyle = HapticStyle.Light)

/**
 * Haptic feedback intensity styles.
 * Maps to platform-specific haptic types.
 */
enum class HapticStyle {
    Light,
    Medium,
    Heavy
}

