package org.androdevlinux.utxo.widget.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.androdevlinux.utxo.widget.FavoritesWidgetProvider

object WidgetRefreshHelper {
    private const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    private const val REQUEST_CODE_REFRESH = 1001

    fun startPeriodicRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FavoritesWidgetProvider::class.java).apply {
            action = FavoritesWidgetProvider.ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Check if we have permission to set exact alarms (API 31+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + REFRESH_INTERVAL_MS,
                    pendingIntent
                )
            } else {
                // Fallback to inexact alarm if exact alarm permission not granted
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + REFRESH_INTERVAL_MS,
                    pendingIntent
                )
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + REFRESH_INTERVAL_MS,
                pendingIntent
            )
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis(),
                REFRESH_INTERVAL_MS,
                pendingIntent
            )
        }
    }

    fun stopPeriodicRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FavoritesWidgetProvider::class.java).apply {
            action = FavoritesWidgetProvider.ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleNextRefresh(context: Context) {
        startPeriodicRefresh(context)
    }
}

