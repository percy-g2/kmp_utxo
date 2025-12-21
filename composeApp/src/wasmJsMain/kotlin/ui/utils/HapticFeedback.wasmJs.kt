package ui.utils

/**
 * WebAssembly/JavaScript implementation of haptic feedback.
 * No-op implementation - web platforms don't have haptic feedback.
 * This ensures the function exists and doesn't crash, but does nothing.
 */
actual fun triggerHapticFeedback(style: HapticStyle) {
    // Web/Wasm platforms don't support haptic feedback
    // This is a safe no-op that will never crash
}

