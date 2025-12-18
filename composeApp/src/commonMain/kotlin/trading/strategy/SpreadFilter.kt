package trading.strategy

import trading.data.MarketSnapshot

/**
 * Filters trades based on spread and liquidity conditions
 * 
 * WHY: Wide spreads indicate low liquidity and high slippage risk.
 * We reject trades when spread is too wide or depth is insufficient.
 * 
 * Rules:
 * - Reject if spread % > maxSpreadPct
 * - Reject if depth cannot absorb 2% of order size
 */
class SpreadFilter(
    private val maxSpreadPct: Double = 0.001,  // 0.1% default max spread
    private val minDepthBufferPct: Double = 0.02  // 2% depth buffer
) {
    
    /**
     * Checks if spread is acceptable for trading
     * 
     * WHY: Wide spreads mean high transaction costs and slippage risk
     * 
     * @param snapshot Market snapshot
     * @return True if spread is acceptable
     */
    fun isSpreadAcceptable(snapshot: MarketSnapshot): Boolean {
        return snapshot.spreadPct <= maxSpreadPct
    }
    
    /**
     * Checks if order book has sufficient depth for a given order size
     * 
     * WHY: Insufficient depth leads to slippage and partial fills
     * 
     * @param snapshot Market snapshot
     * @param orderSizeQuote Order size in quote currency (e.g., USDT)
     * @param isBuy True for buy orders, false for sell orders
     * @return True if depth is sufficient
     */
    fun hasSufficientDepth(
        snapshot: MarketSnapshot,
        orderSizeQuote: Double,
        isBuy: Boolean
    ): Boolean {
        val requiredDepth = orderSizeQuote * (1.0 + minDepthBufferPct)
        val availableDepth = if (isBuy) {
            snapshot.getAskDepth()
        } else {
            snapshot.getBidDepth()
        }
        
        return availableDepth >= requiredDepth
    }
    
    /**
     * Main filter function - checks all spread and liquidity conditions
     * 
     * WHY: Single entry point for all spread/liquidity checks
     * 
     * @param snapshot Market snapshot
     * @param orderSizeQuote Order size in quote currency
     * @param isBuy True for buy orders
     * @return True if all conditions pass
     */
    fun pass(
        snapshot: MarketSnapshot,
        orderSizeQuote: Double,
        isBuy: Boolean
    ): Boolean {
        // Check spread
        if (!isSpreadAcceptable(snapshot)) {
            return false
        }
        
        // Check depth
        if (!hasSufficientDepth(snapshot, orderSizeQuote, isBuy)) {
            return false
        }
        
        return true
    }
    
    /**
     * Returns rejection reason if filter fails, null if passes
     * 
     * WHY: Helps with logging and debugging rejected trades
     */
    fun getRejectionReason(
        snapshot: MarketSnapshot,
        orderSizeQuote: Double,
        isBuy: Boolean
    ): String? {
        if (!isSpreadAcceptable(snapshot)) {
            return "Spread too wide: ${snapshot.spreadPct * 100}% > ${maxSpreadPct * 100}%"
        }
        
        if (!hasSufficientDepth(snapshot, orderSizeQuote, isBuy)) {
            val requiredDepth = orderSizeQuote * (1.0 + minDepthBufferPct)
            val availableDepth = if (isBuy) {
                snapshot.getAskDepth()
            } else {
                snapshot.getBidDepth()
            }
            return "Insufficient depth: required $requiredDepth, available $availableDepth"
        }
        
        return null
    }
}

