package trading.strategy

import trading.data.MarketSnapshot
import trading.data.OrderBook

/**
 * Calculates safe position sizes based on order book depth and risk parameters
 * 
 * WHY: Position sizing is critical for risk management. We must:
 * 1. Never exceed available liquidity (2% of visible depth max)
 * 2. Never risk more than 0.5% of equity per trade
 * 3. Account for slippage and fees
 * 
 * No fixed lot sizes - all sizing is dynamic based on market conditions.
 */
class PositionSizer(
    private val maxDepthPct: Double = 0.02,           // Max 2% of visible depth
    private val maxRiskPerTradePct: Double = 0.005,   // Max 0.5% equity risk per trade
    private val slippageBufferPct: Double = 0.001,     // 0.1% slippage buffer
    private val feePct: Double = 0.001                 // 0.1% trading fee (maker/taker avg)
) {
    
    /**
     * Calculates maximum position size based on order book depth
     * 
     * WHY: Never trade more than 2% of visible depth to avoid market impact
     * 
     * @param snapshot Market snapshot
     * @param isBuy True for buy orders
     * @return Maximum position size in quote currency
     */
    fun calculateMaxDepthBasedSize(
        snapshot: MarketSnapshot,
        isBuy: Boolean
    ): Double {
        val availableDepth = if (isBuy) {
            snapshot.getAskDepth()
        } else {
            snapshot.getBidDepth()
        }
        
        return availableDepth * maxDepthPct
    }
    
    /**
     * Calculates maximum position size based on risk per trade
     * 
     * WHY: Never risk more than 0.5% of equity per trade
     * 
     * @param snapshot Market snapshot
     * @param equity Total account equity
     * @param stopLossPct Stop loss as percentage of entry price (optional)
     * @return Maximum position size in quote currency
     */
    fun calculateMaxRiskBasedSize(
        snapshot: MarketSnapshot,
        equity: Double,
        stopLossPct: Double? = null
    ): Double {
        val maxRiskAmount = equity * maxRiskPerTradePct
        
        // If stop loss is provided, use it to calculate position size
        // Otherwise, assume 1% stop loss as conservative default
        val effectiveStopLossPct = stopLossPct ?: 0.01
        
        // Position size = risk amount / (stop loss % + slippage + fees)
        val totalCostPct = effectiveStopLossPct + slippageBufferPct + (feePct * 2) // Entry + exit fees
        val maxPositionSize = maxRiskAmount / totalCostPct
        
        return maxPositionSize
    }
    
    /**
     * Calculates safe position size considering all constraints
     * 
     * WHY: Takes the minimum of all constraints to ensure we never exceed any limit
     * 
     * @param snapshot Market snapshot
     * @param equity Total account equity
     * @param isBuy True for buy orders
     * @param stopLossPct Optional stop loss percentage
     * @return Safe position size in quote currency, or 0.0 if constraints cannot be met
     */
    fun calculate(
        snapshot: MarketSnapshot,
        equity: Double,
        isBuy: Boolean,
        stopLossPct: Double? = null
    ): Double {
        val depthBasedSize = calculateMaxDepthBasedSize(snapshot, isBuy)
        val riskBasedSize = calculateMaxRiskBasedSize(snapshot, equity, stopLossPct)
        
        // Take minimum to ensure we don't exceed any constraint
        val safeSize = minOf(depthBasedSize, riskBasedSize)
        
        // Ensure minimum viable size (e.g., at least $10 worth)
        val minSize = 10.0
        return if (safeSize >= minSize) safeSize else 0.0
    }
    
    /**
     * Calculates base quantity (e.g., BTC amount) from quote currency size
     * 
     * WHY: Binance API requires quantity in base currency
     * 
     * @param quoteSize Position size in quote currency
     * @param entryPrice Entry price
     * @return Base quantity
     */
    fun calculateBaseQuantity(quoteSize: Double, entryPrice: Double): Double {
        return quoteSize / entryPrice
    }
    
    /**
     * Adjusts quantity to Binance precision requirements
     * 
     * WHY: Binance has minimum quantity and step size requirements
     * 
     * @param quantity Base quantity
     * @param stepSize Step size (e.g., 0.00001 for BTC)
     * @param minQuantity Minimum quantity allowed
     * @return Adjusted quantity meeting precision requirements
     */
    fun adjustQuantity(
        quantity: Double,
        stepSize: Double = 0.00001,
        minQuantity: Double = 0.001
    ): Double {
        // Round down to step size
        val steps = (quantity / stepSize).toInt()
        val adjusted = steps * stepSize
        
        // Ensure minimum quantity
        return maxOf(adjusted, minQuantity)
    }
}

