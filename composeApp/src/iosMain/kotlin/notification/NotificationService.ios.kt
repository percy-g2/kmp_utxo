package notification

import domain.model.PriceAlert
import domain.model.TickerUpdate
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual class NotificationService actual constructor() {
    actual fun requestPermission(onResult: (granted: Boolean) -> Unit) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted: Boolean, _ ->
            dispatch_async(dispatch_get_main_queue()) {
                onResult(granted)
            }
        }
    }

    actual fun sendPriceAlert(
        alert: PriceAlert,
        ticker: TickerUpdate,
    ) {
        val content = UNMutableNotificationContent()
        content.setTitle(alert.notificationTitle())
        content.setBody(alert.notificationBody(ticker))
        val userInfo = mutableMapOf<Any?, Any>()
        userInfo["symbol"] = alert.symbol
        content.setUserInfo(userInfo)

        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false)
        val request =
            UNNotificationRequest.requestWithIdentifier(
                identifier = alert.id,
                content = content,
                trigger = trigger,
            )
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { _ -> }
    }

    actual fun cancelAlert(alertId: String) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(alertId))
        UNUserNotificationCenter.currentNotificationCenter()
            .removeDeliveredNotificationsWithIdentifiers(listOf(alertId))
    }

    actual fun areNotificationsEnabled(): Boolean = true
}
