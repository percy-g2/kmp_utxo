package notification

import domain.model.PriceAlert
import domain.model.TickerUpdate

expect class NotificationService() {
    fun requestPermission(onResult: (granted: Boolean) -> Unit)

    fun sendPriceAlert(
        alert: PriceAlert,
        ticker: TickerUpdate,
    )

    fun cancelAlert(alertId: String)

    fun areNotificationsEnabled(): Boolean
}
