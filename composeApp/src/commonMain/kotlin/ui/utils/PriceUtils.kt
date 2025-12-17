package ui.utils

import androidx.compose.ui.graphics.Color
import theme.greenDark
import theme.greenLight
import theme.redDark

/**
 * Shared utilities for price-related calculations and color determination.
 * Follows CMP best practices by extracting reusable logic.
 */

/**
 * Determines the color for price change display based on the change percentage and theme.
 * 
 * @param priceChangePercent The price change percentage as a string
 * @param isDarkTheme Whether dark theme is active
 * @param primaryColor The primary color to use when change is zero
 * @return The appropriate color for the price change
 */
fun getPriceChangeColor(priceChangePercent: String, isDarkTheme: Boolean, primaryColor: Color): Color {
    val priceChangeFloat = priceChangePercent.toFloatOrNull() ?: 0f
    return getPriceChangeColor(priceChangeFloat, isDarkTheme, primaryColor)
}

/**
 * Determines the color for price change display based on the change percentage and theme.
 * Uses a Float value directly for better performance when already parsed.
 */
fun getPriceChangeColor(priceChangeFloat: Float, isDarkTheme: Boolean, primaryColor: Color): Color {
    return when {
        priceChangeFloat > 0f -> if (isDarkTheme) greenDark else greenLight
        priceChangeFloat < 0f -> redDark
        else -> primaryColor
    }
}

/**
 * Creates gradient colors for chart fill based on price change color.
 */
fun createPriceChangeGradientColors(priceChangeColor: Color): List<Color> {
    return listOf(
        priceChangeColor.copy(alpha = 0.6f),
        priceChangeColor.copy(alpha = 0.3f),
        priceChangeColor.copy(alpha = 0.1f),
        priceChangeColor.copy(alpha = 0f)
    )
}

