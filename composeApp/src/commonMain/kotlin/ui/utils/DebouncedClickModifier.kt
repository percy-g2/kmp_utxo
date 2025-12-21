package ui.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Extension function that adds debounced click handling with optional haptic feedback.
 * 
 * Prevents rapid taps from triggering actions by enforcing a minimum time interval
 * between accepted clicks. Only clicks that pass the debounce check trigger haptic feedback.
 * 
 * Features:
 * - 500ms debounce by default (configurable)
 * - Per-instance debounce state (stable across recompositions)
 * - Platform-aware haptic feedback (only on accepted clicks)
 * - Preserves ripple effects and accessibility semantics
 * 
 * @param enabled Whether the clickable is enabled
 * @param debounceMillis Minimum time in milliseconds between accepted clicks (default: 500ms)
 * @param haptic Whether to trigger haptic feedback on accepted clicks (default: true)
 * @param onClick The action to perform when a click is accepted (not debounced)
 * @param role The semantic role of this clickable (default: Role.Button)
 * @param interactionSource Optional interaction source for tracking interaction state
 * 
 * @sample
 * ```
 * Modifier.debouncedClickable(
 *     enabled = true,
 *     debounceMillis = 1000L,
 *     haptic = true,
 *     onClick = { /* handle click */ }
 * )
 * ```
 */
@OptIn(ExperimentalTime::class)
@Composable
fun Modifier.debouncedClickable(
    enabled: Boolean = true,
    debounceMillis: Long = 1000L,
    haptic: Boolean = true,
    role: Role = Role.Button,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    // Remember the last click timestamp per composable instance
    // Using a simple class wrapper ensures state is stable across recompositions
    // without triggering recomposition when the timestamp changes
    val lastClickTimeHolder = remember { LastClickTimeHolder() }
    
    return this.clickable(
        enabled = enabled,
        onClickLabel = null, // Preserve default accessibility behavior
        role = role,
        interactionSource = interactionSource,
        onClick = {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val timeSinceLastClick = currentTime - lastClickTimeHolder.value
            
            // Only accept click if enough time has passed since last click
            if (timeSinceLastClick >= debounceMillis) {
                // Update timestamp before triggering action
                lastClickTimeHolder.value = currentTime
                
                // Trigger haptic feedback only on accepted clicks
                if (haptic) {
                    triggerHapticFeedback(HapticStyle.Light)
                }
                
                // Execute the click action
                onClick()
            }
            // If debounced, silently ignore the click (no haptic, no action)
        }
    )
}

/**
 * Simple holder for last click timestamp that's stable across recompositions.
 * This avoids triggering recomposition when the timestamp changes, as we only
 * need to track state internally for debouncing purposes.
 */
private class LastClickTimeHolder(initialValue: Long = 0L) {
    var value: Long = initialValue
}

