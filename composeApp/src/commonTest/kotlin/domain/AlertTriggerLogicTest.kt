package domain

import domain.model.AlertCondition
import domain.model.PriceAlert
import domain.model.TickerUpdate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertTriggerLogicTest {
    private fun alert(condition: AlertCondition): PriceAlert =
        PriceAlert(
            id = "1",
            symbol = "BTCUSDT",
            displayName = "BTC/USDT",
            condition = condition,
            isEnabled = true,
            createdAt = 0L,
            lastTriggeredAt = null,
            repeatAfterMinutes = 60,
        )

    @Test
    fun priceAboveTriggers() {
        val a = alert(AlertCondition.PriceAbove(100.0))
        assertTrue(AlertTriggerLogic.isTriggered(a, TickerUpdate("BTCUSDT", 101.0, 0.0)))
        assertFalse(AlertTriggerLogic.isTriggered(a, TickerUpdate("BTCUSDT", 99.0, 0.0)))
    }

    @Test
    fun priceBelowTriggers() {
        val a = alert(AlertCondition.PriceBelow(100.0))
        assertTrue(AlertTriggerLogic.isTriggered(a, TickerUpdate("BTCUSDT", 99.0, 0.0)))
        assertFalse(AlertTriggerLogic.isTriggered(a, TickerUpdate("BTCUSDT", 100.0, 0.0)))
    }

    @Test
    fun percentUpTriggers() {
        val a = alert(AlertCondition.PercentChangeUp(5.0))
        assertTrue(AlertTriggerLogic.isTriggered(a, TickerUpdate("BTCUSDT", 1.0, 5.5)))
        assertFalse(AlertTriggerLogic.isTriggered(a, TickerUpdate("BTCUSDT", 1.0, 4.0)))
    }

    @Test
    fun percentDownTriggers() {
        val a = alert(AlertCondition.PercentChangeDown(5.0))
        assertTrue(AlertTriggerLogic.isTriggered(a, TickerUpdate("BTCUSDT", 1.0, -5.5)))
        assertFalse(AlertTriggerLogic.isTriggered(a, TickerUpdate("BTCUSDT", 1.0, -4.0)))
    }

    @Test
    fun cooldownSkipsRecentTrigger() {
        val a = alert(AlertCondition.PriceAbove(1.0)).copy(lastTriggeredAt = 1_000_000L)
        assertFalse(AlertTriggerLogic.shouldCheckCooldown(a, 1_000_000L + 30_000L))
        assertTrue(AlertTriggerLogic.shouldCheckCooldown(a, 1_000_000L + 61 * 60_000L))
    }
}
