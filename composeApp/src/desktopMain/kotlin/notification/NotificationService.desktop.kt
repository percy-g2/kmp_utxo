package notification

import domain.model.PriceAlert
import domain.model.TickerUpdate
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

actual class NotificationService actual constructor() {
    private var trayIcon: TrayIcon? = null

    actual fun requestPermission(onResult: (granted: Boolean) -> Unit) {
        onResult(SystemTray.isSupported())
    }

    actual fun sendPriceAlert(
        alert: PriceAlert,
        ticker: TickerUpdate,
    ) {
        if (!SystemTray.isSupported()) return
        SwingUtilities.invokeLater {
            try {
                val tray = SystemTray.getSystemTray()
                if (trayIcon == null) {
                    val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                    trayIcon = TrayIcon(image, "UTXO")
                    tray.add(trayIcon)
                }
                trayIcon?.displayMessage(
                    alert.notificationTitle(),
                    alert.notificationBody(ticker),
                    TrayIcon.MessageType.WARNING,
                )
            } catch (_: Exception) {
                // Tray unavailable or headless
            }
        }
    }

    actual fun cancelAlert(alertId: String) {
        // Tray API has no per-message cancel
    }

    actual fun areNotificationsEnabled(): Boolean = SystemTray.isSupported()
}
