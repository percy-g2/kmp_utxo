package trading.risk

import logging.AppLogger
import model.OrderBookData
import trading.data.MarketSnapshot
import trading.data.TradeFlowMetrics

/**
 * Central risk management system enforcing all risk rules
 * 
 * WHY: Single point of control for all risk checks ensures consistency
 * and prevents bypassing safety rules.
 * 
 * Hard Rules:
 * - Max daily loss: 2%
 * - Max consecutive losses: 3
 * - Cooldown after loss streak
 * - No trade during extreme volatility spikes
 */
class RiskManager(
    private val dailyLossGuard: DailyLossGuard,
    private val maxConsecutiveLosses: Int = 3,
    private val cooldownAfterLossesMs: Long = 60000,  // 1 minute cooldown
    private val maxVolatilityPct: Double = 0.05  // 5% max volatility (reject if exceeded)
) {
    private var consecutiveLosses: Int = 0
    private var lastLossTime: Long = 0
    private var isInCooldown: Boolean = false
    
    /**
     * Main risk check - determines if trading is allowed
     * 
     * WHY: All trades must pass this check before execution
     * 
     * @param snapshot Market snapshot for volatility check
     * @return True if trading is allowed
     */
    fun canTrade(snapshot: MarketSnapshot): Boolean {
        // Check daily loss limit
        if (dailyLossGuard.hasExceededDailyLoss()) {
            AppLogger.logger.w { 
                "Trading blocked: Daily loss limit exceeded. Daily P&L: ${dailyLossGuard.getDailyPnL()}" 
            }
            return false
        }
        
        // Check consecutive losses and cooldown
        if (isInCooldown) {
            val timeSinceLastLoss = System.currentTimeMillis() - lastLossTime
            if (timeSinceLastLoss < cooldownAfterLossesMs) {
                AppLogger.logger.d { 
                    "Trading blocked: In cooldown period. Time remaining: ${cooldownAfterLossesMs - timeSinceLastLoss}ms" 
                }
                return false
            } else {
                // Cooldown expired
                isInCooldown = false
            }
        }
        
        if (consecutiveLosses >= maxConsecutiveLosses) {
            isInCooldown = true
            AppLogger.logger.w { 
                "Trading blocked: Max consecutive losses reached ($consecutiveLosses). Entering cooldown." 
            }
            return false
        }
        
        // Check volatility (simple spread-based check)
        // In production, use more sophisticated volatility measure (e.g., ATR)
        if (snapshot.spreadPct > maxVolatilityPct) {
            AppLogger.logger.d { 
                "Trading blocked: Volatility too high. Spread: ${snapshot.spreadPct * 100}%" 
            }
            return false
        }
        
        return true
    }
    
    /**
     * Records a winning trade
     * 
     * WHY: Resets consecutive loss counter
     */
    fun recordWin(pnl: Double) {
        consecutiveLosses = 0
        isInCooldown = false
        dailyLossGuard.recordTrade(pnl)
        AppLogger.logger.d { "RiskManager: Win recorded. P&L: $pnl, Consecutive losses reset." }
    }
    
    /**
     * Records a losing trade
     * 
     * WHY: Tracks consecutive losses and triggers cooldown if needed
     */
    fun recordLoss(pnl: Double) {
        consecutiveLosses++
        lastLossTime = System.currentTimeMillis()
        dailyLossGuard.recordTrade(pnl)
        
        AppLogger.logger.w { 
            "RiskManager: Loss recorded. P&L: $pnl, Consecutive losses: $consecutiveLosses/$maxConsecutiveLosses" 
        }
        
        if (consecutiveLosses >= maxConsecutiveLosses) {
            isInCooldown = true
            AppLogger.logger.w { 
                "RiskManager: Max consecutive losses reached. Entering cooldown for ${cooldownAfterLossesMs}ms" 
            }
        }
    }
    
    /**
     * Gets current risk status for logging/monitoring
     */
    fun getRiskStatus(): RiskStatus {
        return RiskStatus(
            dailyPnL = dailyLossGuard.getDailyPnL(),
            dailyLossPct = dailyLossGuard.getDailyLossPct(),
            consecutiveLosses = consecutiveLosses,
            isInCooldown = isInCooldown,
            canTrade = canTrade(MarketSnapshot(
                symbol = "",
                orderBook = OrderBookData(
                    symbol = "",
                    bids = emptyList(),
                    asks = emptyList(),
                    lastUpdateId = 0,
                    timestamp = 0
                ),
                tradeFlow = TradeFlowMetrics(
                    aggressiveBuyVolume = 0.0,
                    aggressiveSellVolume = 0.0,
                    totalVolume = 0.0,
                    buyPressureRatio = 1.0,
                    sellPressureRatio = 1.0,
                    sampleCount = 0,
                    timeWindowMs = 5000
                ),
                bestBid = 0.0,
                bestAsk = 0.0,
                midPrice = 0.0,
                spread = 0.0,
                spreadPct = 0.0,
                timestamp = System.currentTimeMillis()
            ))
        )
    }
    
    /**
     * Resets risk manager (for testing)
     */
    fun reset() {
        consecutiveLosses = 0
        isInCooldown = false
        lastLossTime = 0
        dailyLossGuard.reset()
    }
}

/**
 * Risk status snapshot
 */
data class RiskStatus(
    val dailyPnL: Double,
    val dailyLossPct: Double,
    val consecutiveLosses: Int,
    val isInCooldown: Boolean,
    val canTrade: Boolean
)

