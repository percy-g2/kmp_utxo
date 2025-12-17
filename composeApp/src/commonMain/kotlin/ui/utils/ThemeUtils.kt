package ui.utils

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import ui.AppTheme
import ui.Settings

/**
 * Shared theme utilities following CMP best practices.
 * Extracts common theme calculation logic to avoid duplication.
 */

/**
 * Calculates if dark theme should be used based on settings and system preference.
 * This is a reusable composable function that can be called from any composable.
 */
@Composable
fun isDarkTheme(settings: Settings?): Boolean {
    val systemIsDarkTheme = isSystemInDarkTheme()
    return when (settings?.appTheme) {
        AppTheme.Dark -> true
        AppTheme.Light -> false
        else -> systemIsDarkTheme // Default to system when settings is null
    }
}

