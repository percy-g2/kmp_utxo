package domain

import domain.model.AlertCondition
import domain.model.PriceAlert
import domain.model.TickerUpdate

object AlertTriggerLogic {
    fun isTriggered(
        alert: PriceAlert,
        ticker: TickerUpdate,
    ): Boolean =
        when (val c = alert.condition) {
            is AlertCondition.PriceAbove -> ticker.lastPrice > c.price
            is AlertCondition.PriceBelow -> ticker.lastPrice < c.price
            is AlertCondition.PercentChangeUp -> ticker.percentChange24h >= c.percent
            is AlertCondition.PercentChangeDown -> ticker.percentChange24h <= -kotlin.math.abs(c.percent)
        }

    /**
     * Returns true when the alert should be considered for firing (cooldown elapsed).
     */
    fun shouldCheckCooldown(
        alert: PriceAlert,
        nowMillis: Long,
    ): Boolean {
        val last = alert.lastTriggeredAt ?: return true
        val cooldownMs = alert.repeatAfterMinutes * 60_000L
        return nowMillis - last >= cooldownMs
    }
}
