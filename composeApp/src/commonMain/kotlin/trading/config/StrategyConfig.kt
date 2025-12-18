package trading.config

/**
 * Strategy configuration - all thresholds in one place
 * 
 * WHY: No hardcoded magic numbers - all parameters are configurable
 * This allows easy optimization and testing without code changes
 */
data class StrategyConfig(
    // Order book imbalance thresholds
    val imbalanceLong: Double = 1.5,          // Imbalance > 1.5 → LONG candidate
    val imbalanceShort: Double = 0.67,       // Imbalance < 0.67 → SHORT candidate
    val topNLevels: Int = 20,                 // Number of order book levels to analyze
    
    // Spread and liquidity filters
    val maxSpreadPct: Double = 0.001,         // 0.1% max spread
    val minDepthBufferPct: Double = 0.02,     // 2% depth buffer requirement
    
    // Trade flow confirmation
    val tradeFlowThreshold: Double = 1.5,     // Buy/sell pressure ratio threshold
    val tradeFlowWindowMs: Long = 5000,       // 5 second window for trade flow analysis
    val minTradeFlowSamples: Int = 5,          // Minimum trades for reliable analysis
    
    // Position sizing
    val maxDepthPct: Double = 0.02,            // Max 2% of visible depth
    val maxRiskPerTradePct: Double = 0.005,    // Max 0.5% equity risk per trade
    val slippageBufferPct: Double = 0.001,    // 0.1% slippage buffer
    val feePct: Double = 0.001,                // 0.1% trading fee
    
    // Risk management
    val maxDailyLossPct: Double = 0.02,        // 2% max daily loss
    val maxConsecutiveLosses: Int = 3,         // Max consecutive losses before cooldown
    val cooldownAfterLossesMs: Long = 60000,  // 1 minute cooldown
    val maxVolatilityPct: Double = 0.05,      // 5% max volatility (reject if exceeded)
    
    // Execution policy
    val preferMaker: Boolean = true,           // Prefer maker orders
    val makerSpreadThreshold: Double = 0.0005, // 0.05% - use maker if spread tight
    val momentumThreshold: Double = 1.2        // Use market order if momentum exceeds
) {
    companion object {
        /**
         * Default configuration for production
         */
        val DEFAULT = StrategyConfig()
        
        /**
         * Conservative configuration (tighter risk limits)
         */
        val CONSERVATIVE = StrategyConfig(
            imbalanceLong = 2.0,
            imbalanceShort = 0.5,
            maxSpreadPct = 0.0005,
            maxRiskPerTradePct = 0.002,
            maxDailyLossPct = 0.01,
            maxConsecutiveLosses = 2
        )
        
        /**
         * Aggressive configuration (looser risk limits)
         */
        val AGGRESSIVE = StrategyConfig(
            imbalanceLong = 1.3,
            imbalanceShort = 0.77,
            maxSpreadPct = 0.002,
            maxRiskPerTradePct = 0.01,
            maxDailyLossPct = 0.03,
            maxConsecutiveLosses = 5
        )
    }
}

