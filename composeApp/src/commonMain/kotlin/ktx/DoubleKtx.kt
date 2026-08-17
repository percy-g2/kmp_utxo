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
 * Formats an asset quantity as grouped fixed-point text without ever exposing scientific notation.
 * Large token balances need fewer fractional digits to stay readable, while smaller balances retain
 * enough precision to remain useful:
 *
 * - 1,000,000 and above: up to 2 fractional digits
 * - 1,000 and above: up to 4 fractional digits
 * - below 1,000: up to 6 fractional digits
 *
 * The whole and fractional parts are rounded separately so a large value is never multiplied by the
 * decimal scale (which would overflow the old formatter above roughly 9.22 trillion).
 */
internal fun Double.formatAsAssetAmount(): String {
    if (!isFinite()) return "—"

    val magnitude = absoluteValue
    // The formatter stores the grouped whole part in a Long. Values at this boundary have already
    // exceeded useful integer precision in a Double; fail closed instead of letting toLong() saturate
    // and a rounding carry wrap Long.MAX_VALUE into a negative amount.
    if (magnitude >= Long.MAX_VALUE.toDouble()) return "—"

    val decimalPlaces = when {
        magnitude >= 1_000_000.0 -> 2
        magnitude >= 1_000.0 -> 4
        else -> 6
    }
    val scale = when (decimalPlaces) {
        2 -> 100L
        4 -> 10_000L
        else -> 1_000_000L
    }

    var whole = magnitude.toLong()
    var fraction = ((magnitude - whole.toDouble()) * scale).roundToInt().toLong()
    if (fraction >= scale) {
        whole += 1
        fraction -= scale
    }

    val isRoundedZero = whole == 0L && fraction == 0L
    val sign = if (this < 0.0 && !isRoundedZero) "-" else ""
    val fractionText = fraction
        .toString()
        .padStart(decimalPlaces, '0')
        .trimEnd('0')

    return buildString {
        append(sign)
        append(groupThousands(whole))
        if (fractionText.isNotEmpty()) {
            append('.')
            append(fractionText)
        }
    }
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
