package ui.utils

/** Null means the feed cannot describe a valid range; never display a fabricated midpoint. */
internal fun dailyRangePosition(lastPrice: String, lowPrice: String, highPrice: String): Float? {
    val last = lastPrice.toDoubleOrNull() ?: return null
    val low = lowPrice.toDoubleOrNull() ?: return null
    val high = highPrice.toDoubleOrNull() ?: return null
    if (!last.isFinite() || !low.isFinite() || !high.isFinite() || high <= low) return null
    val fraction = (last - low) / (high - low)
    return if (fraction.isFinite()) fraction.coerceIn(0.0, 1.0).toFloat() else null
}
