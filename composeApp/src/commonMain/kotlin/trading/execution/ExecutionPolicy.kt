package trading.execution

import trading.strategy.TradeSignal

/**
 * Execution policy determining order type (MAKER vs TAKER)
 * 
 * WHY: Maker orders pay lower fees but may not fill immediately.
 * Taker orders fill immediately but pay higher fees.
 * We choose based on market conditions and urgency.
 */
enum class OrderType {
    LIMIT_MAKER,    // Post limit order (maker fee ~0.1%)
    LIMIT_TAKER,    // Aggressive limit order (taker fee ~0.1%)
    MARKET          // Market order (taker fee ~0.1%, immediate fill)
}

/**
 * Execution policy for determining order type and parameters
 */
class ExecutionPolicy(
    private val preferMaker: Boolean = true,      // Prefer maker orders when possible
    private val makerSpreadThreshold: Double = 0.0005,  // 0.05% - use maker if spread tight
    private val momentumThreshold: Double = 1.2   // Use taker if momentum exceeds threshold
) {
    
    /**
     * Determines order type based on market conditions
     * 
     * WHY: 
     * - Maker orders: Use when spread is tight and price is stable (lower fees)
     * - Taker orders: Use when momentum is high and trade flow is accelerating (ensure fill)
     * 
     * @param signal Trading signal
     * @param spreadPct Current spread percentage
     * @param momentum Momentum indicator (trade flow acceleration)
     * @return Recommended order type
     */
    fun determineOrderType(
        signal: TradeSignal,
        spreadPct: Double,
        momentum: Double = 1.0
    ): OrderType {
        // If momentum is very high, use market order to ensure fill
        if (momentum > momentumThreshold) {
            return OrderType.MARKET
        }
        
        // If spread is tight and we prefer maker, use limit maker
        if (preferMaker && spreadPct <= makerSpreadThreshold) {
            return OrderType.LIMIT_MAKER
        }
        
        // Default to limit taker (aggressive limit order)
        return OrderType.LIMIT_TAKER
    }
    
    /**
     * Calculates limit price for maker orders
     * 
     * WHY: Maker orders must be placed at or better than current best bid/ask
     * 
     * @param signal Trading signal
     * @param bestBid Current best bid
     * @param bestAsk Current best ask
     * @return Limit price for maker order
     */
    fun calculateMakerPrice(
        signal: TradeSignal,
        bestBid: Double,
        bestAsk: Double
    ): Double {
        return when (signal) {
            is TradeSignal.Long -> {
                // For LONG: place bid slightly below best bid to be maker
                bestBid * 0.9999
            }
            is TradeSignal.Short -> {
                // For SHORT: place ask slightly above best ask to be maker
                bestAsk * 1.0001
            }
            else -> bestBid
        }
    }
    
    /**
     * Calculates limit price for taker orders
     * 
     * WHY: Taker orders must cross the spread to fill immediately
     * 
     * @param signal Trading signal
     * @param bestBid Current best bid
     * @param bestAsk Current best ask
     * @return Limit price for taker order
     */
    fun calculateTakerPrice(
        signal: TradeSignal,
        bestBid: Double,
        bestAsk: Double
    ): Double {
        return when (signal) {
            is TradeSignal.Long -> {
                // For LONG: pay best ask to fill immediately
                bestAsk
            }
            is TradeSignal.Short -> {
                // For SHORT: sell at best bid to fill immediately
                bestBid
            }
            else -> bestBid
        }
    }
}

