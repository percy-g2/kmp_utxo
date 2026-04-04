package org.androdevlinux.utxo

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import data.repository.AlertRepositoryImpl
import domain.runSinglePriceAlertPoll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import network.HttpClient
import notification.NotificationService

/**
 * Foreground service that polls Binance REST while enabled price alerts exist.
 * Keeps evaluation alive when the UI process may tear down the market WebSocket.
 */
class PriceAlertService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var pollJob: Job? = null
    private val httpClient = HttpClient()
    private val alertRepository = AlertRepositoryImpl()
    private val notificationService = NotificationService()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureMonitorChannel()
        startAsForeground()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                pollJob?.cancel()
                pollJob = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startAsForeground()
                startPolling()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pollJob?.cancel()
        serviceScope.cancel()
        runCatching { httpClient.close() }
        super.onDestroy()
    }

    private fun ensureMonitorChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel =
            android.app.NotificationChannel(
                MONITOR_CHANNEL_ID,
                MONITOR_CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    private fun startAsForeground() {
        val notification =
            NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
                .setContentTitle("UTXO")
                .setContentText("Monitoring your price alerts")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                MONITOR_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(MONITOR_NOTIFICATION_ID, notification)
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob =
            serviceScope.launch {
                while (isActive) {
                    runCatching {
                        runSinglePriceAlertPoll(alertRepository, notificationService, httpClient)
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
    }

    companion object {
        const val ACTION_START = "org.androdevlinux.utxo.START_ALERT_SERVICE"
        const val ACTION_STOP = "org.androdevlinux.utxo.STOP_ALERT_SERVICE"

        private const val MONITOR_CHANNEL_ID = "utxo_price_alert_monitor"
        private const val MONITOR_CHANNEL_NAME = "Price alert monitoring"
        private const val MONITOR_NOTIFICATION_ID = 7102
        private const val POLL_INTERVAL_MS = 30_000L
    }
}
