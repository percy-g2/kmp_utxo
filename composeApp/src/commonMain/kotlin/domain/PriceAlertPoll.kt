@file:OptIn(kotlin.time.ExperimentalTime::class)

package domain

import data.repository.AlertRepository
import domain.model.toTickerUpdate
import model.Ticker24hr
import model.TickerData
import network.HttpClient
import notification.NotificationService
import kotlin.time.Clock

/**
 * Single REST poll for enabled alerts (used by Android foreground service and tests).
 */
suspend fun runSinglePriceAlertPoll(
    alertRepository: AlertRepository,
    notificationService: NotificationService,
    httpClient: HttpClient,
) {
    val alerts = alertRepository.getAlerts().filter { it.isEnabled }
    if (alerts.isEmpty()) return

    val symbols = alerts.map { it.symbol }.distinct()
    val tickers24: Map<String, Ticker24hr> = httpClient.fetchTickers24hr(symbols)
    val tickerMap: Map<String, TickerData> =
        tickers24.mapValues { (_, t) ->
            TickerData(
                symbol = t.symbol,
                lastPrice = t.lastPrice,
                priceChangePercent = t.priceChangePercent,
                volume = t.quoteVolume,
            )
        }
    val now = Clock.System.now().toEpochMilliseconds()
    for (alert in alerts) {
        if (!AlertTriggerLogic.shouldCheckCooldown(alert, now)) continue
        val data = tickerMap[alert.symbol] ?: continue
        val update = data.toTickerUpdate()
        if (AlertTriggerLogic.isTriggered(alert, update)) {
            notificationService.sendPriceAlert(alert, update)
            alertRepository.markTriggered(alert.id, Clock.System.now().toEpochMilliseconds())
        }
    }
}
