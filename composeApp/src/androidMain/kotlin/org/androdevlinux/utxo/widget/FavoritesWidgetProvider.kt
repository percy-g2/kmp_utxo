package org.androdevlinux.utxo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.androdevlinux.utxo.MainActivity
import org.androdevlinux.utxo.R
import org.androdevlinux.utxo.widget.service.FavoritesWidgetService

class FavoritesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all widgets
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Start the service when the first widget is added
        FavoritesWidgetService.startPeriodicUpdates(context)
    }

    override fun onDisabled(context: Context) {
        // Stop the service when the last widget is removed
        FavoritesWidgetService.stopPeriodicUpdates(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        // Handle manual refresh
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, FavoritesWidgetProvider::class.java)
            )
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
            // Schedule next refresh after manual refresh
            org.androdevlinux.utxo.widget.helper.WidgetRefreshHelper.scheduleNextRefresh(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "org.androdevlinux.utxo.widget.ACTION_REFRESH"
        private const val MAX_FAVORITES = 4

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Create RemoteViews for the widget
            val views = RemoteViews(context.packageName, R.layout.widget_favorites)
            
            // Set up click intent to open the app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            // Set up refresh button
            val refreshIntent = Intent(context, FavoritesWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

            // Show initial state
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
            views.setTextViewText(R.id.widget_empty_text, "Loading...")
            views.setViewVisibility(R.id.widget_empty_text, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_favorites_container, android.view.View.GONE)
            
            // Update the widget immediately with loading state
            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            // Update widget data directly (no service needed on Android 12+)
            org.androdevlinux.utxo.widget.helper.WidgetUpdateHelper.updateWidget(context, appWidgetId)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, FavoritesWidgetProvider::class.java)
            )
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}

