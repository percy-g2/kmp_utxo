package ktx

import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt

fun Double.formatAsCurrency(): String {
    val absValue = this.absoluteValue
    val integerPart = absValue.toInt()
    val fractionalPart = ((absValue - integerPart) * 100).roundToInt()

    val formattedInteger = integerPart.toString().reversed().chunked(3).joinToString(",").reversed()
    val formattedFractional = fractionalPart.toString().padStart(2, '0')

    return if (this < 0) "-$formattedInteger.$formattedFractional" else "$formattedInteger.$formattedFractional"
}

/**
 * Formats a Double to a string with specified decimal places
 * KMP-compatible alternative to String.format("%.Nf", value)
 * 
 * @param decimals Number of decimal places (default: 2)
 * @return Formatted string
 */
fun Double.formatDecimal(decimals: Int = 2): String {
    val multiplier = 10.0.pow(decimals)
    val rounded = (this * multiplier).roundToInt() / multiplier
    val absRounded = kotlin.math.abs(rounded)
    
    val integerPart = absRounded.toInt()
    val fractionalPart = ((absRounded - integerPart) * multiplier).roundToInt()
    
    val fractionalStr = fractionalPart.toString().padStart(decimals, '0')
    
    return if (this < 0) "-$integerPart.$fractionalStr" else "$integerPart.$fractionalStr"
}