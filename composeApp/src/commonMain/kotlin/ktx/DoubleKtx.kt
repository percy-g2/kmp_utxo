package ktx

import kotlin.math.absoluteValue
import kotlin.math.roundToInt

fun Double.formatAsCurrency(): String {
    val absValue = this.absoluteValue
    val integerPart = absValue.toLong()
    val fractionalPart = ((absValue - integerPart) * 100).roundToInt()

    val formattedInteger = groupThousands(integerPart)
    val formattedFractional = fractionalPart.toString().padStart(2, '0')

    return if (this < 0) "-$formattedInteger.$formattedFractional" else "$formattedInteger.$formattedFractional"
}

/**
 * Inserts thousands separators in a single pass. Replaces the
 * `toString().reversed().chunked(3).joinToString(",").reversed()` idiom (which allocated several
 * intermediate strings + a list per call) — this runs while formatting prices for the whole
 * market on every ticker frame. Expects a non-negative value.
 */
internal fun groupThousands(value: Long): String {
    val s = value.toString()
    if (s.length <= 3) return s
    val sb = StringBuilder(s.length + (s.length - 1) / 3)
    val firstGroup = s.length % 3
    if (firstGroup > 0) sb.append(s, 0, firstGroup)
    var i = firstGroup
    while (i < s.length) {
        if (sb.isNotEmpty()) sb.append(',')
        sb.append(s, i, i + 3)
        i += 3
    }
    return sb.toString()
}