package notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import domain.model.PriceAlert
import domain.model.TickerUpdate
import org.androdevlinux.utxo.ContextProvider

actual class NotificationService actual constructor() {
    private val context get() = ContextProvider.getContext()

    actual fun requestPermission(onResult: (granted: Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(true)
            return
        }
        val activity = context.findActivity()
        if (activity != null) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS,
            )
            // Result delivered via Activity; caller should re-check in onRequestPermissionsResult or resume.
            onResult(
                ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        } else {
            val intent =
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            onResult(areNotificationsEnabled())
        }
    }

    actual fun sendPriceAlert(
        alert: PriceAlert,
        ticker: TickerUpdate,
    ) {
        ensureChannel()
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return

        val launchIntent =
            Intent().apply {
                setClassName(context.packageName, "org.androdevlinux.utxo.MainActivity")
                putExtra("coin_symbol", alert.symbol)
                putExtra("coin_display_symbol", alert.displayName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                alert.id.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(alert.notificationTitle())
                .setContentText(alert.notificationBody(ticker))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        notificationManager.notify(alert.id.hashCode(), notification)
    }

    actual fun cancelAlert(alertId: String) {
        NotificationManagerCompat.from(context).cancel(alertId.hashCode())
    }

    actual fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        val channel =
            android.app.NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "utxo_price_alerts"
        const val CHANNEL_NAME = "Price Alerts"
        const val CHANNEL_DESCRIPTION = "Notifications for your cryptocurrency price targets"
        const val REQUEST_POST_NOTIFICATIONS = 9101
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }
