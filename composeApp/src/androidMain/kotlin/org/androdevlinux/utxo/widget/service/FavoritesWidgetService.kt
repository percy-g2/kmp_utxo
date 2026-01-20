package org.androdevlinux.utxo.widget.service

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.androdevlinux.utxo.R
import org.androdevlinux.utxo.widget.FavoritesWidgetProvider
import org.androdevlinux.utxo.widget.helper.ChartHelper
import org.androdevlinux.utxo.widget.helper.SettingsHelper
import org.androdevlinux.utxo.widget.helper.TickerDataHelper
import org.androdevlinux.utxo.widget.helper.WidgetRefreshHelper
import ui.AppTheme
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import ktx.toCryptoSymbol

class FavoritesWidgetService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_WIDGET -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    updateWidget(appWidgetId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun updateWidget(appWidgetId: Int) {
        android.util.Log.d("WidgetService", "updateWidget called for appWidgetId: $appWidgetId")
        val views = RemoteViews(applicationContext.packageName, R.layout.widget_favorites)
        
        // Set up click intent to open the app
        val intent = android.content.Intent().apply {
            setClassName(applicationContext, "org.androdevlinux.utxo.MainActivity")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        // Set up refresh button
        val refreshIntent = android.content.Intent(applicationContext, org.androdevlinux.utxo.widget.FavoritesWidgetProvider::class.java).apply {
            action = org.androdevlinux.utxo.widget.FavoritesWidgetProvider.ACTION_REFRESH
        }
        val refreshPendingIntent = android.app.PendingIntent.getBroadcast(
            applicationContext,
            0,
            refreshIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
        
        updateWidgetContent(appWidgetId, views)
    }
    
    private fun updateWidgetContent(appWidgetId: Int, views: RemoteViews) {
        serviceScope.launch {
            try {
                // Read favorites from settings
                val settings = SettingsHelper.readSettings(applicationContext)
                android.util.Log.d("WidgetService", "Settings: $settings")
                val favorites = settings?.favPairs?.filter { it.isNotEmpty() && it.isNotBlank() }?.take(MAX_FAVORITES) ?: emptyList()
                android.util.Log.d("WidgetService", "Favorites count: ${favorites.size}, Favorites: $favorites")
                
                // Determine theme
                val isDarkTheme = when (settings?.appTheme) {
                    AppTheme.Dark -> true
                    AppTheme.Light -> false
                    else -> {
                        // Check system theme
                        val nightModeFlags = applicationContext.resources.configuration.uiMode and
                                android.content.res.Configuration.UI_MODE_NIGHT_MASK
                        nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    }
                }
                
                // Apply theme colors
                val backgroundColor = if (isDarkTheme) {
                    Color.parseColor("#121212") // Material Dark background
                } else {
                    Color.parseColor("#FFFFFF") // White background
                }
                
                val textColor = if (isDarkTheme) {
                    Color.parseColor("#FFFFFF") // White text
                } else {
                    Color.parseColor("#000000") // Black text
                }
                
                val secondaryTextColor = if (isDarkTheme) {
                    Color.parseColor("#B3B3B3") // Light gray
                } else {
                    Color.parseColor("#666666") // Dark gray
                }
                
                // Set widget background with rounded corners
                val backgroundRes = if (isDarkTheme) {
                    R.drawable.widget_background_dark
                } else {
                    R.drawable.widget_background
                }
                views.setInt(R.id.widget_container, "setBackgroundResource", backgroundRes)
                views.setTextColor(R.id.widget_title, textColor)
                views.setTextColor(R.id.widget_empty_text, secondaryTextColor)

                // Update widget title
                views.setTextViewText(R.id.widget_title, applicationContext.getString(R.string.widget_title))
                
                if (favorites.isEmpty()) {
                    // Show empty state
                    android.util.Log.d("WidgetService", "No favorites found, showing empty state")
                    views.setTextViewText(R.id.widget_empty_text, applicationContext.getString(R.string.widget_no_favorites))
                    views.setViewVisibility(R.id.widget_empty_text, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_favorites_container, android.view.View.GONE)
                    
                    // Update widget immediately with empty state
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } else {
                    // Hide empty state
                    views.setViewVisibility(R.id.widget_empty_text, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_favorites_container, android.view.View.VISIBLE)

                    // Fetch ticker data for favorites
                    android.util.Log.d("WidgetService", "Fetching ticker data for: $favorites")
                    val tickerDataMap = TickerDataHelper.fetchTickers(applicationContext, favorites, json)
                    android.util.Log.d("WidgetService", "Ticker data fetched: ${tickerDataMap.keys}")

                    // Update widget views for each favorite (up to 4)
                    val itemIds = listOf(
                        R.id.widget_item_1,
                        R.id.widget_item_2,
                        R.id.widget_item_3,
                        R.id.widget_item_4
                    )

                    favorites.forEachIndexed { index, symbol ->
                        if (index < MAX_FAVORITES) {
                            val tickerData = tickerDataMap[symbol]
                            val itemId = itemIds[index]
                            
                            // Fetch chart data
                            val klines = ChartHelper.fetchChartData(symbol)
                            
                            if (tickerData != null) {
                                // Show the item
                                views.setViewVisibility(itemId, android.view.View.VISIBLE)
                                
                                // Set symbol parts separately to match favorites screen style
                                val (baseSymbol, quoteSymbol) = extractSymbolParts(symbol, settings?.selectedTradingPair ?: "BTC")
                                views.setTextViewText(getSymbolBaseViewId(itemId), baseSymbol)
                                views.setTextColor(getSymbolBaseViewId(itemId), textColor)
                                views.setTextViewText(getSymbolSeparatorViewId(itemId), "/")
                                views.setTextColor(getSymbolSeparatorViewId(itemId), textColor)
                                views.setTextViewText(getSymbolQuoteViewId(itemId), quoteSymbol)
                                views.setTextColor(getSymbolQuoteViewId(itemId), secondaryTextColor)
                                
                                // Set volume (format like favorites screen)
                                val formattedVolume = formatVolume(tickerData.volume)
                                views.setTextViewText(getVolumeViewId(itemId), formattedVolume)
                                views.setTextColor(getVolumeViewId(itemId), secondaryTextColor)
                                
                                // Set price with trading pair symbol
                                views.setTextViewText(
                                    getPriceViewId(itemId),
                                    formatPrice(tickerData.lastPrice, quoteSymbol)
                                )
                                views.setTextColor(getPriceViewId(itemId), textColor)
                                
                                // Set change percentage with color
                                val changePercent = tickerData.priceChangePercent.toDoubleOrNull() ?: 0.0
                                val changeText = formatChangePercent(changePercent)
                                views.setTextViewText(
                                    getChangeViewId(itemId),
                                    changeText
                                )
                                
                                // Set color based on positive/negative change
                                val changeColor = if (changePercent >= 0) {
                                    Color.parseColor("#4CAF50") // Green
                                } else {
                                    Color.parseColor("#F44336") // Red
                                }
                                views.setTextColor(
                                    getChangeViewId(itemId),
                                    changeColor
                                )
                                
                                // Create and set chart bitmap
                                if (klines.isNotEmpty()) {
                                    val chartBitmap = ChartHelper.createSparklineBitmap(
                                        klines = klines,
                                        width = 80,
                                        height = 40,
                                        lineColor = changeColor,
                                        fillColor = changeColor,
                                        isPositive = changePercent >= 0
                                    )
                                    views.setImageViewBitmap(getChartViewId(itemId), chartBitmap)
                                    views.setViewVisibility(getChartViewId(itemId), android.view.View.VISIBLE)
                                } else {
                                    views.setViewVisibility(getChartViewId(itemId), android.view.View.GONE)
                                }
                            } else {
                                // Show symbol but indicate loading/error
                                views.setViewVisibility(itemId, android.view.View.VISIBLE)
                                val (baseSymbol, quoteSymbol) = extractSymbolParts(symbol, settings?.selectedTradingPair ?: "BTC")
                                views.setTextViewText(getSymbolBaseViewId(itemId), baseSymbol)
                                views.setTextColor(getSymbolBaseViewId(itemId), textColor)
                                views.setTextViewText(getSymbolSeparatorViewId(itemId), "/")
                                views.setTextColor(getSymbolSeparatorViewId(itemId), textColor)
                                views.setTextViewText(getSymbolQuoteViewId(itemId), quoteSymbol)
                                views.setTextColor(getSymbolQuoteViewId(itemId), secondaryTextColor)
                                views.setTextViewText(getVolumeViewId(itemId), "--")
                                views.setTextColor(getVolumeViewId(itemId), secondaryTextColor)
                                views.setTextViewText(
                                    getPriceViewId(itemId),
                                    "--"
                                )
                                views.setTextColor(getPriceViewId(itemId), secondaryTextColor)
                                views.setTextViewText(
                                    getChangeViewId(itemId),
                                    "--"
                                )
                                views.setTextColor(getChangeViewId(itemId), secondaryTextColor)
                                views.setViewVisibility(getChartViewId(itemId), android.view.View.GONE)
                            }
                        }
                    }

                    // Hide unused items
                    for (i in favorites.size until MAX_FAVORITES) {
                        views.setViewVisibility(itemIds[i], android.view.View.GONE)
                    }
                    
                    android.util.Log.d("WidgetService", "All items updated, updating widget now")
                }

                // Update the widget on main thread
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                        android.util.Log.d("WidgetService", "Widget successfully updated for appWidgetId: $appWidgetId")
                    } catch (e: Exception) {
                        android.util.Log.e("WidgetService", "Failed to update widget", e)
                        throw e
                    }
                }
                
                // Schedule next refresh
                WidgetRefreshHelper.scheduleNextRefresh(applicationContext)

            } catch (e: Exception) {
                android.util.Log.e("WidgetService", "Error updating widget", e)
                e.printStackTrace()
                // Still try to update widget with error state
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e2: Exception) {
                    android.util.Log.e("WidgetService", "Error updating widget after exception", e2)
                }
            } finally {
                stopSelf()
            }
        }
    }

    private fun extractSymbolParts(symbol: String, selectedTradingPair: String): Pair<String, String> {
        // Common quote currencies (sorted by length descending to match longer ones first)
        val quoteCurrencies = listOf("USDT", "USDC", "BUSD", "FDUSD", "BTC", "ETH", "BNB", "DAI", "TUSD", "EUR", "GBP", "JPY")
        
        // First try with selected trading pair
        if (symbol.endsWith(selectedTradingPair, ignoreCase = true) && symbol.length > selectedTradingPair.length) {
            val base = symbol.substring(0, symbol.length - selectedTradingPair.length)
            if (base.isNotEmpty()) {
                return Pair(base, selectedTradingPair)
            }
        }
        
        // Fallback: try to extract from symbol using common quote currencies
        for (quote in quoteCurrencies.sortedByDescending { it.length }) {
            if (symbol.endsWith(quote, ignoreCase = true) && symbol.length > quote.length) {
                val base = symbol.substring(0, symbol.length - quote.length)
                if (base.isNotEmpty()) {
                    return Pair(base, quote)
                }
            }
        }
        
        // Last resort: return symbol as-is
        return Pair(symbol, "")
    }

    private fun formatPrice(price: String, quoteSymbol: String): String {
        val priceValue = price.toDoubleOrNull() ?: return price
        val formattedPrice = when {
            priceValue >= 1000 -> DecimalFormat("#,###", DecimalFormatSymbols(Locale.US)).format(priceValue)
            priceValue >= 1 -> DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(priceValue)
            priceValue >= 0.01 -> DecimalFormat("#,##0.0000", DecimalFormatSymbols(Locale.US)).format(priceValue)
            else -> DecimalFormat("#,##0.00000000", DecimalFormatSymbols(Locale.US)).format(priceValue)
        }
        // Append trading pair symbol if available
        return if (quoteSymbol.isNotEmpty()) {
            "$formattedPrice ${quoteSymbol.toCryptoSymbol()}"
        } else {
            formattedPrice
        }
    }

    private fun formatChangePercent(changePercent: Double): String {
        val sign = if (changePercent >= 0) "+" else ""
        return "$sign${String.format(Locale.US, "%.2f", changePercent)}%"
    }

    private fun formatVolume(volume: String): String {
        return try {
            val value = volume.toDouble()
            when {
                value >= 1_000_000_000 -> {
                    val billions = value / 1_000_000_000
                    when {
                        billions >= 100 -> "${billions.toInt()}B"
                        billions >= 10 -> "${(billions * 10).toInt() / 10.0}B"
                        else -> "${(billions * 100).toInt() / 100.0}B"
                    }
                }
                value >= 1_000_000 -> {
                    val millions = value / 1_000_000
                    when {
                        millions >= 100 -> "${millions.toInt()}M"
                        millions >= 10 -> "${(millions * 10).toInt() / 10.0}M"
                        else -> "${(millions * 100).toInt() / 100.0}M"
                    }
                }
                value >= 1_000 -> {
                    value.toInt().toString()
                        .reversed()
                        .chunked(3)
                        .joinToString(",")
                        .reversed()
                }
                value >= 1 -> {
                    "${(value * 100).toInt() / 100.0}"
                }
                else -> {
                    "${(value * 10000).toInt() / 10000.0}"
                }
            }
        } catch (e: Exception) {
            volume
        }
    }

    private fun getSymbolBaseViewId(itemId: Int): Int {
        return when (itemId) {
            R.id.widget_item_1 -> R.id.widget_item_1_symbol_base
            R.id.widget_item_2 -> R.id.widget_item_2_symbol_base
            R.id.widget_item_3 -> R.id.widget_item_3_symbol_base
            R.id.widget_item_4 -> R.id.widget_item_4_symbol_base
            else -> R.id.widget_item_1_symbol_base
        }
    }

    private fun getSymbolSeparatorViewId(itemId: Int): Int {
        return when (itemId) {
            R.id.widget_item_1 -> R.id.widget_item_1_symbol_separator
            R.id.widget_item_2 -> R.id.widget_item_2_symbol_separator
            R.id.widget_item_3 -> R.id.widget_item_3_symbol_separator
            R.id.widget_item_4 -> R.id.widget_item_4_symbol_separator
            else -> R.id.widget_item_1_symbol_separator
        }
    }

    private fun getSymbolQuoteViewId(itemId: Int): Int {
        return when (itemId) {
            R.id.widget_item_1 -> R.id.widget_item_1_symbol_quote
            R.id.widget_item_2 -> R.id.widget_item_2_symbol_quote
            R.id.widget_item_3 -> R.id.widget_item_3_symbol_quote
            R.id.widget_item_4 -> R.id.widget_item_4_symbol_quote
            else -> R.id.widget_item_1_symbol_quote
        }
    }

    private fun getVolumeViewId(itemId: Int): Int {
        return when (itemId) {
            R.id.widget_item_1 -> R.id.widget_item_1_volume
            R.id.widget_item_2 -> R.id.widget_item_2_volume
            R.id.widget_item_3 -> R.id.widget_item_3_volume
            R.id.widget_item_4 -> R.id.widget_item_4_volume
            else -> R.id.widget_item_1_volume
        }
    }

    private fun getPriceViewId(itemId: Int): Int {
        return when (itemId) {
            R.id.widget_item_1 -> R.id.widget_item_1_price
            R.id.widget_item_2 -> R.id.widget_item_2_price
            R.id.widget_item_3 -> R.id.widget_item_3_price
            R.id.widget_item_4 -> R.id.widget_item_4_price
            else -> R.id.widget_item_1_price
        }
    }

    private fun getChangeViewId(itemId: Int): Int {
        return when (itemId) {
            R.id.widget_item_1 -> R.id.widget_item_1_change
            R.id.widget_item_2 -> R.id.widget_item_2_change
            R.id.widget_item_3 -> R.id.widget_item_3_change
            R.id.widget_item_4 -> R.id.widget_item_4_change
            else -> R.id.widget_item_1_change
        }
    }

    private fun getChartViewId(itemId: Int): Int {
        return when (itemId) {
            R.id.widget_item_1 -> R.id.widget_item_1_chart
            R.id.widget_item_2 -> R.id.widget_item_2_chart
            R.id.widget_item_3 -> R.id.widget_item_3_chart
            R.id.widget_item_4 -> R.id.widget_item_4_chart
            else -> R.id.widget_item_1_chart
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "org.androdevlinux.utxo.widget.UPDATE_WIDGET"
        private const val MAX_FAVORITES = 4
        private const val UPDATE_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

        fun startUpdate(context: Context, appWidgetId: Int) {
            // Use WidgetUpdateHelper instead of service (works on Android 12+)
            org.androdevlinux.utxo.widget.helper.WidgetUpdateHelper.updateWidget(context, appWidgetId)
        }

        fun startPeriodicUpdates(context: Context) {
            WidgetRefreshHelper.startPeriodicRefresh(context)
        }

        fun stopPeriodicUpdates(context: Context) {
            WidgetRefreshHelper.stopPeriodicRefresh(context)
        }
    }
}

