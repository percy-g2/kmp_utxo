package trading.strategy

import trading.data.AggTrade
import trading.data.MarketSnapshot
import trading.data.TradeFlowMetrics

/**
 * Analyzes aggressive trade flow to confirm order book imbalance signals
 * 
 * WHY: Order book imbalance alone can be misleading (could be spoofing).
 * Aggressive trade flow (taker trades) confirms actual buying/selling pressure.
 * We only trade when both imbalance AND trade flow agree on direction.
 * 
 * Aggressive buying = buyer is taker (isBuyerMaker=false)
 * Aggressive selling = seller is taker (isBuyerMaker=true)
 */
class TradeFlowAnalyzer(
    private val confirmationThreshold: Double = 1.5  // Buy/sell pressure ratio threshold
) {
    
    /**
     * Calculates trade flow metrics from a list of aggregated trades
     * 
     * WHY: Aggregates recent trades to determine overall market sentiment
     * 
     * @param trades List of aggregated trades (most recent first)
     * @param timeWindowMs Time window in milliseconds (default 5000ms = 5 seconds)
     * @return Trade flow metrics
     */
    fun calculateMetrics(
        trades: List<AggTrade>,
        timeWindowMs: Long = 5000
    ): TradeFlowMetrics {
        if (trades.isEmpty()) {
            return TradeFlowMetrics(
                aggressiveBuyVolume = 0.0,
                aggressiveSellVolume = 0.0,
                totalVolume = 0.0,
                buyPressureRatio = 1.0,
                sellPressureRatio = 1.0,
                sampleCount = 0,
                timeWindowMs = timeWindowMs
            )
        }
        
        val cutoffTime = trades.first().timestamp - timeWindowMs
        val recentTrades = trades.takeWhile { it.timestamp >= cutoffTime }
        
        var aggressiveBuyVolume = 0.0
        var aggressiveSellVolume = 0.0
        
        recentTrades.forEach { trade ->
            if (trade.isAggressiveBuy) {
                aggressiveBuyVolume += trade.tradeValue
            } else if (trade.isAggressiveSell) {
                aggressiveSellVolume += trade.tradeValue
            }
        }
        
        val totalVolume = aggressiveBuyVolume + aggressiveSellVolume
        
        val buyPressureRatio = if (aggressiveSellVolume > 0.0) {
            aggressiveBuyVolume / aggressiveSellVolume
        } else if (aggressiveBuyVolume > 0.0) {
            Double.MAX_VALUE // Infinite buy pressure
        } else {
            1.0 // No trades
        }
        
        val sellPressureRatio = if (aggressiveBuyVolume > 0.0) {
            aggressiveSellVolume / aggressiveBuyVolume
        } else if (aggressiveSellVolume > 0.0) {
            Double.MAX_VALUE // Infinite sell pressure
        } else {
            1.0 // No trades
        }
        
        return TradeFlowMetrics(
            aggressiveBuyVolume = aggressiveBuyVolume,
            aggressiveSellVolume = aggressiveSellVolume,
            totalVolume = totalVolume,
            buyPressureRatio = buyPressureRatio,
            sellPressureRatio = sellPressureRatio,
            sampleCount = recentTrades.size,
            timeWindowMs = timeWindowMs
        )
    }
    
    /**
     * Checks if trade flow confirms a LONG signal (aggressive buying)
     * 
     * WHY: Only trade LONG when both order book imbalance AND trade flow indicate buying
     */
    fun confirmsLong(metrics: TradeFlowMetrics): Boolean {
        return metrics.hasStrongBuyFlow(confirmationThreshold)
    }
    
    /**
     * Checks if trade flow confirms a SHORT signal (aggressive selling)
     * 
     * WHY: Only trade SHORT when both order book imbalance AND trade flow indicate selling
     */
    fun confirmsShort(metrics: TradeFlowMetrics): Boolean {
        return metrics.hasStrongSellFlow(confirmationThreshold)
    }
    
    /**
     * Evaluates if trade flow confirms the given signal direction
     * 
     * @param snapshot Market snapshot containing trade flow metrics
     * @param isLong True if checking LONG confirmation, false for SHORT
     * @return True if trade flow confirms the signal
     */
    fun confirmsSignal(snapshot: MarketSnapshot, isLong: Boolean): Boolean {
        return if (isLong) {
            confirmsLong(snapshot.tradeFlow)
        } else {
            confirmsShort(snapshot.tradeFlow)
        }
    }
    
    /**
     * Checks if trade flow has sufficient sample size for reliable analysis
     * 
     * WHY: Need minimum number of trades to avoid false signals from noise
     */
    fun hasSufficientSamples(metrics: TradeFlowMetrics, minSamples: Int = 5): Boolean {
        return metrics.sampleCount >= minSamples && metrics.totalVolume > 0.0
    }
}

