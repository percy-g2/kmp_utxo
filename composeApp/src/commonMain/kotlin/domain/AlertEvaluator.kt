@file:OptIn(kotlin.time.ExperimentalTime::class)

package domain

import data.repository.AlertRepository
import domain.model.toTickerUpdateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import model.TickerData
import notification.NotificationService
import kotlin.time.Clock

/**
 * Evaluates enabled price alerts against a shared ticker map (e.g. WebSocket mini-ticker aggregate).
 */
class AlertEvaluator(
    private val alertRepository: AlertRepository,
    private val notificationService: NotificationService,
) {
    fun startEvaluating(
        scope: CoroutineScope,
        tickerMapFlow: Flow<Map<String, TickerData>>,
    ) {
        scope.launch {
            tickerMapFlow
                .map { it.toTickerUpdateMap() }
                .collect { tickerMap ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    val alerts =
                        alertRepository
                            .getAlerts()
                            .filter { it.isEnabled }
                            .filter { AlertTriggerLogic.shouldCheckCooldown(it, now) }

                    for (alert in alerts) {
                        val ticker = tickerMap[alert.symbol] ?: continue
                        if (AlertTriggerLogic.isTriggered(alert, ticker)) {
                            notificationService.sendPriceAlert(alert, ticker)
                            alertRepository.markTriggered(alert.id, Clock.System.now().toEpochMilliseconds())
                        }
                    }
                }
        }
    }
}
