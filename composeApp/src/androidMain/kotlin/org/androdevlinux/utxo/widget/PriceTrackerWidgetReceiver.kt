package org.androdevlinux.utxo.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.CryptoPair
import model.SymbolPriceTicker
import network.HttpClient
import okio.Path.Companion.toPath
import org.androdevlinux.utxo.MainActivity
import theme.DarkColorScheme
import theme.LightColorScheme
import ui.Settings

class PriceTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "UPDATE_WIDGET_DATA") {
            // Trigger the WorkManager task to update the widget
            val workRequest = OneTimeWorkRequestBuilder<UpdateWidgetWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleWidgetUpdate(context)
    }

    override val glanceAppWidget: GlanceAppWidget
        get() = TrackerWidget
}

object TrackerWidget : GlanceAppWidget() {


    fun updateAllWidgets(context: Context) {
        val glanceAppWidgetManager = GlanceAppWidgetManager(context)
        val glanceIds = runBlocking { glanceAppWidgetManager.getGlanceIds(TrackerWidget::class.java) }
        glanceIds.forEach { glanceId ->
            runBlocking {
                update(context, glanceId)
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        provideContent {
            val isSystemInDarkTheme = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

            val coroutineScope = rememberCoroutineScope()
            var tickers by remember { mutableStateOf<List<SymbolPriceTicker>>(listOf()) }

            val store = storeOf<Settings>(
                file = "${context.cacheDir?.absolutePath}/settings.json".toPath(),
                default = Settings(
                    selectedTheme = 0,
                    favPairs = listOf(CryptoPair.BTCUSDT.symbol, CryptoPair.ETHUSDT.symbol, CryptoPair.SOLUSDT.symbol)
                )
            )

            val sharedPreferences = context.getSharedPreferences("crypto_widget_cache", Context.MODE_PRIVATE)
            val cachedDataJson = sharedPreferences.getString("cached_data", null)

            // Parse cached data if available
            cachedDataJson?.let {
                tickers = Json.decodeFromString(it)
            }

            val colorScheme = when (isSystemInDarkTheme) {
                false -> LightColorScheme
                else -> DarkColorScheme
            }

            val httpClient = HttpClient()

            // Fetch the symbol price using the fetchSymbolPriceTicker method
            LaunchedEffect(Unit) {
                coroutineScope.launch {
                    val symbols = store.get()?.favPairs ?: emptyList()
                    tickers = httpClient.fetchSymbolPriceTicker(symbols) ?: emptyList()
                }
            }


            Box(
                modifier = GlanceModifier
                    .clickable(actionStartActivity<MainActivity>())
                    .fillMaxSize()
                    .background(colorScheme.background)
            ) {

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    if (tickers.isEmpty()) {
                        Text(
                            text = "No data available",
                            style = TextStyle(color = ColorProvider(color = colorScheme.onBackground))
                        )
                    } else {
                        tickers.forEach { tickerData ->
                            Text(
                                text = "${tickerData.symbol}: ${tickerData.price}",
                                style = TextStyle(color = ColorProvider(color = colorScheme.onBackground))
                            )
                        }
                    }
                }
            }
        }
    }
}

suspend fun fetchAndCacheData(context: Context) {
    val store = storeOf<Settings>(
        file = "${context.cacheDir?.absolutePath}/settings.json".toPath(),
        default = Settings(
            selectedTheme = 0,
            favPairs = listOf(CryptoPair.BTCUSDT.symbol, CryptoPair.ETHUSDT.symbol, CryptoPair.SOLUSDT.symbol)
        )
    )
    val settings = store.updates.first()
    val favPairs = settings?.favPairs ?: emptyList()
    val httpClient = HttpClient()

    withContext(Dispatchers.IO) {
        val tickerDataMap = httpClient.fetchSymbolPriceTicker(favPairs) ?: emptyList()
        val sharedPreferences = context.getSharedPreferences("crypto_widget_cache", Context.MODE_PRIVATE)
        sharedPreferences.edit().apply {
            putString("cached_data", Json.encodeToString(tickerDataMap))
            apply()
        }
    }
}

