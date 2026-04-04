package notification

import domain.model.AlertCondition
import domain.model.PriceAlert
import domain.model.TickerUpdate

fun PriceAlert.conditionSummary(): String =
    when (val c = condition) {
        is AlertCondition.PriceAbove -> "Price above ${c.price}"
        is AlertCondition.PriceBelow -> "Price below ${c.price}"
        is AlertCondition.PercentChangeUp -> "24h change up ${c.percent}%+"
        is AlertCondition.PercentChangeDown -> "24h change down ${c.percent}%-"
    }

fun PriceAlert.notificationTitle(): String = "$displayName alert triggered"

fun PriceAlert.notificationBody(ticker: TickerUpdate): String = "${conditionSummary()} — Current: ${ticker.lastPrice}"
