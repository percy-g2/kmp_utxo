package notification

import domain.model.PriceAlert
import domain.model.TickerUpdate

/**
 * Web: notifications only if the browser grants permission; no background evaluation.
 * Full Web Notifications API wiring can be added with platform-specific JS interop.
 */
actual class NotificationService actual constructor() {
    actual fun requestPermission(onResult: (granted: Boolean) -> Unit) {
        onResult(false)
    }

    actual fun sendPriceAlert(
        alert: PriceAlert,
        ticker: TickerUpdate,
    ) {
        // Best-effort: in-app toast/banner should be used when permission is denied (see UI layer).
    }

    actual fun cancelAlert(alertId: String) {
    }

    actual fun areNotificationsEnabled(): Boolean = false
}
