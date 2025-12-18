package trading.strategy

import trading.data.MarketSnapshot
import trading.data.OrderBook

/**
 * Calculates order book imbalance to identify trading opportunities
 * 
 * WHY: Order book imbalance is a leading indicator of price movement.
 * When bid volume significantly exceeds ask volume, there's buying pressure.
 * When ask volume significantly exceeds bid volume, there's selling pressure.
 * 
 * Rules:
 * - Imbalance > 1.5 → LONG candidate (strong buy pressure)
 * - Imbalance < 0.67 → SHORT candidate (strong sell pressure)
 * - 0.67 <= Imbalance <= 1.5 → Neutral (no clear signal)
 */
class ImbalanceCalculator(
    private val longThreshold: Double = 1.5,
    private val shortThreshold: Double = 0.67,
    private val topNLevels: Int = 20
) {
    
    /**
     * Calculates order book imbalance ratio
     * 
     * @param snapshot Market snapshot
     * @return Imbalance ratio (bidVolume / askVolume)
     */
    fun calculateImbalance(snapshot: MarketSnapshot): Double {
        return OrderBook.calculateImbalance(snapshot.orderBook, topNLevels)
    }
    
    /**
     * Determines if imbalance suggests a LONG signal
     * 
     * WHY: When bid volume is 1.5x ask volume, there's significant buying pressure
     * indicating potential upward price movement
     */
    fun suggestsLong(imbalance: Double): Boolean {
        return imbalance > longThreshold
    }
    
    /**
     * Determines if imbalance suggests a SHORT signal
     * 
     * WHY: When ask volume is 1.5x bid volume (imbalance < 0.67), there's significant
     * selling pressure indicating potential downward price movement
     */
    fun suggestsShort(imbalance: Double): Boolean {
        return imbalance < shortThreshold
    }
    
    /**
     * Calculates confidence score based on imbalance strength
     * 
     * WHY: Stronger imbalance = higher confidence in signal direction
     * 
     * @param imbalance Imbalance ratio
     * @param isLong True for LONG signals, false for SHORT
     * @return Confidence score 0.0 to 1.0
     */
    fun calculateConfidence(imbalance: Double, isLong: Boolean): Double {
        return if (isLong) {
            // For LONG: confidence increases as imbalance increases above threshold
            val excess = imbalance - longThreshold
            val maxExcess = 2.0 // Imbalance of 3.5 = max confidence
            (excess / maxExcess).coerceIn(0.0, 1.0)
        } else {
            // For SHORT: confidence increases as imbalance decreases below threshold
            val deficit = shortThreshold - imbalance
            val maxDeficit = 0.33 // Imbalance of 0.34 = max confidence
            (deficit / maxDeficit).coerceIn(0.0, 1.0)
        }
    }
    
    /**
     * Evaluates market snapshot and returns imbalance-based signal direction
     * 
     * @param snapshot Market snapshot
     * @return Signal direction: 1.0 for LONG, -1.0 for SHORT, 0.0 for neutral
     */
    fun evaluate(snapshot: MarketSnapshot): Double {
        val imbalance = calculateImbalance(snapshot)
        
        return when {
            suggestsLong(imbalance) -> 1.0
            suggestsShort(imbalance) -> -1.0
            else -> 0.0
        }
    }
}

