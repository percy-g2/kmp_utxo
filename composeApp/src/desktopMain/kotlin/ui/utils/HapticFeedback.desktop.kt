package ui.utils

/**
 * Desktop implementation of haptic feedback.
 * No-op implementation - desktop platforms don't have haptic feedback.
 * This ensures the function exists and doesn't crash, but does nothing.
 */
actual fun triggerHapticFeedback(style: HapticStyle) {
    // Desktop platforms don't support haptic feedback
    // This is a safe no-op that will never crash
}

