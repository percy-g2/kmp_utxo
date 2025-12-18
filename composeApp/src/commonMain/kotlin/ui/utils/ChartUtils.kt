package ui.utils

import androidx.compose.ui.geometry.Offset
import model.UiKline

/**
 * Shared utility for chart calculations across the app.
 * Follows CMP best practices by extracting reusable logic.
 */

// Optimized: Limit points to reduce memory allocations and improve performance
// iOS can handle ~100-200 points smoothly, more causes jank
private const val MAX_CHART_POINTS = 150

/**
 * Calculates chart points from kline data for rendering.
 * Limits points to MAX_CHART_POINTS for optimal performance.
 */
fun calculateChartPoints(
    klines: List<UiKline>,
    size: Offset,
    minPrice: Float,
    priceRange: Float
): List<Offset> {
    if (klines.isEmpty() || size.x <= 0 || size.y <= 0) return emptyList()
    
    // Limit points to reduce memory allocations - sample if too many
    val sampleStep = if (klines.size > MAX_CHART_POINTS) {
        klines.size / MAX_CHART_POINTS
    } else {
        1
    }
    
    // Pre-allocate list with exact size to avoid reallocations
    val pointCount = (klines.size + sampleStep - 1) / sampleStep
    val points = ArrayList<Offset>(pointCount)
    
    val lastIndex = klines.lastIndex
    var pointIndex = 0
    
    // Use indexed iteration to avoid creating intermediate collections
    for (i in klines.indices step sampleStep) {
        val kline = klines[i]
        // Cache float conversion to avoid repeated parsing
        val price = kline.closePrice.toFloatOrNull() ?: minPrice
        val x = if (lastIndex > 0) {
            i.toFloat() / lastIndex * size.x
        } else {
            size.x / 2f
        }
        val y = size.y - ((price - minPrice) / priceRange) * size.y
        points.add(Offset(x, y))
        pointIndex++
    }
    
    // Always include the last point for accuracy
    if (pointIndex > 0 && pointIndex - 1 < pointCount) {
        val lastKline = klines.last()
        val lastPrice = lastKline.closePrice.toFloatOrNull() ?: minPrice
        val lastX = size.x
        val lastY = size.y - ((lastPrice - minPrice) / priceRange) * size.y
        points[pointIndex - 1] = Offset(lastX, lastY)
    }
    
    return points
}

/**
 * Calculates price statistics (min, max, range) from a list of prices.
 */
fun calculatePriceStats(prices: List<Float>): Triple<Float, Float, Float> {
    return if (prices.isEmpty()) {
        Triple(0f, 0f, 1f)
    } else {
        val max = prices.maxOrNull() ?: 0f
        val min = prices.minOrNull() ?: 0f
        val range = if (max != min) max - min else 1f
        Triple(min, max, range)
    }
}

/**
 * Limits klines to MAX_CHART_POINTS for optimal rendering performance.
 * Uses pre-allocated list to avoid intermediate collections.
 */
fun limitKlinesForChart(klines: List<UiKline>): List<UiKline> {
    return if (klines.size > MAX_CHART_POINTS) {
        val step = klines.size / MAX_CHART_POINTS
        // Pre-allocate list with exact size to avoid reallocations
        val result = ArrayList<UiKline>(MAX_CHART_POINTS + 1) // +1 for last point
        for (i in klines.indices step step) {
            result.add(klines[i])
        }
        // Always include the last point for accuracy if not already included
        val lastKline = klines.last()
        if (result.lastOrNull() != lastKline) {
            result.add(lastKline)
        }
        result
    } else {
        klines
    }
}

