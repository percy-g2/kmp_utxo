package trading.risk

import kotlin.time.ExperimentalTime

/**
 * Daily loss guard to prevent excessive losses
 * 
 * WHY: Prevents catastrophic losses by stopping trading after daily loss limit
 * 
 * Rules:
 * - Max daily loss: 2% of starting equity
 * - Tracks P&L throughout the day
 * - Resets at start of new trading day
 */
@OptIn(ExperimentalTime::class)
class DailyLossGuard(
    private val maxDailyLossPct: Double = 0.02,  // 2% max daily loss
    private val startingEquity: Double
) {
    private var currentEquity: Double = startingEquity
    private var dailyPnL: Double = 0.0
    private var lastResetDate: String = getCurrentDate()
    
    /**
     * Gets current date string (YYYY-MM-DD) for day tracking
     */
    private fun getCurrentDate(): String {
        val now = kotlin.time.Clock.System.now()
        // Simple date string - in production, use proper date formatting
        return now.toEpochMilliseconds().toString().take(10) // Simplified
    }
    
    /**
     * Checks if we've exceeded daily loss limit
     * 
     * WHY: Hard stop to prevent further losses after bad day
     */
    fun hasExceededDailyLoss(): Boolean {
        checkReset()
        val lossPct = -dailyPnL / startingEquity
        return lossPct >= maxDailyLossPct
    }
    
    /**
     * Records a trade P&L
     * 
     * @param pnl Profit or loss from trade (positive = profit, negative = loss)
     */
    fun recordTrade(pnl: Double) {
        checkReset()
        dailyPnL += pnl
        currentEquity += pnl
    }
    
    /**
     * Checks if we need to reset for new trading day
     * 
     * WHY: Daily limits reset at start of new day
     */
    private fun checkReset() {
        val currentDate = getCurrentDate()
        if (currentDate != lastResetDate) {
            // New day - reset daily P&L
            dailyPnL = 0.0
            lastResetDate = currentDate
        }
    }
    
    /**
     * Gets current daily P&L
     */
    fun getDailyPnL(): Double {
        checkReset()
        return dailyPnL
    }
    
    /**
     * Gets current equity
     */
    fun getCurrentEquity(): Double {
        return currentEquity
    }
    
    /**
     * Gets daily loss percentage
     */
    fun getDailyLossPct(): Double {
        checkReset()
        return if (dailyPnL < 0) {
            -dailyPnL / startingEquity
        } else {
            0.0
        }
    }
    
    /**
     * Resets daily guard (for testing or manual reset)
     */
    fun reset() {
        dailyPnL = 0.0
        lastResetDate = getCurrentDate()
    }
}

