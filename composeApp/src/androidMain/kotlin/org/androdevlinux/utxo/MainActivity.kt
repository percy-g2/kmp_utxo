package org.androdevlinux.utxo

import App
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.startup.Initializer
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import model.UiKline
import theme.DarkColorScheme
import theme.LightColorScheme
import theme.ThemeManager
import theme.UTXOTheme
import ui.Theme
import ui.TickerCard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeState by ThemeManager.themeState.collectAsState()
            val view = LocalView.current

            if (!view.isInEditMode) {
                // Determine color scheme based on themeState and system theme
                val colorScheme = when (themeState) {
                    Theme.SYSTEM.id -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
                    Theme.LIGHT.id -> LightColorScheme
                    Theme.DARK.id -> DarkColorScheme
                    else -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
                }

                // Set the system UI bar colors based on the app and system theme
                val barColor = colorScheme.background.toArgb()

                val isSystemInDarkTheme = isSystemInDarkTheme()
                LaunchedEffect(themeState) {
                    if (themeState == Theme.LIGHT.id || (!isSystemInDarkTheme && themeState == Theme.SYSTEM.id)) {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.light(barColor, barColor)
                        )
                    } else {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.dark(barColor)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding())
            ) {
                App()
            }
        }
    }
}

@Preview
@Composable
fun Preview() {
    ContextProvider.setContext(LocalContext.current)
    App()
}

@Preview
@Composable
fun Preview1() {
    ContextProvider.setContext(LocalContext.current)
    val sampleTrades = listOf(
        UiKline(
            openTime = 1633046400000,
            openPrice = "42500.00",
            highPrice = "43000.00",
            lowPrice = "42000.00",
            closePrice = "42800.00",
            volume = "1000.00",
            closeTime = 1633047300000,
            quoteAssetVolume = "42800000.00",
            numberOfTrades = 1000,
            takerBuyBaseAssetVolume = "500.00",
            takerBuyQuoteAssetVolume = "21400000.00"
        ),
        UiKline(
            openTime = 1633047300000,
            openPrice = "42800.00",
            highPrice = "43200.00",
            lowPrice = "42600.00",
            closePrice = "43000.00",
            volume = "800.00",
            closeTime = 1633048200000,
            quoteAssetVolume = "34400000.00",
            numberOfTrades = 800,
            takerBuyBaseAssetVolume = "400.00",
            takerBuyQuoteAssetVolume = "17200000.00"
        ),
        UiKline(
            openTime = 1633048200000,
            openPrice = "43000.00",
            highPrice = "43500.00",
            lowPrice = "42800.00",
            closePrice = "42900.00",
            volume = "1200.00",
            closeTime = 1633049100000,
            quoteAssetVolume = "51840000.00",
            numberOfTrades = 1200,
            takerBuyBaseAssetVolume = "600.00",
            takerBuyQuoteAssetVolume = "25920000.00"
        ),
        UiKline(
            openTime = 1633048200000,
            openPrice = "43000.00",
            highPrice = "43500.00",
            lowPrice = "42800.00",
            closePrice = "42950.00",
            volume = "1200.00",
            closeTime = 1633049100000,
            quoteAssetVolume = "51840000.00",
            numberOfTrades = 1200,
            takerBuyBaseAssetVolume = "600.00",
            takerBuyQuoteAssetVolume = "25920000.00"
        ),
        UiKline(
            openTime = 1633048200000,
            openPrice = "43000.00",
            highPrice = "43500.00",
            lowPrice = "42800.00",
            closePrice = "42960.00",
            volume = "1200.00",
            closeTime = 1633049100000,
            quoteAssetVolume = "51840000.00",
            numberOfTrades = 1200,
            takerBuyBaseAssetVolume = "600.00",
            takerBuyQuoteAssetVolume = "25920000.00"
        ),
        UiKline(
            openTime = 1633048200000,
            openPrice = "43000.00",
            highPrice = "43500.00",
            lowPrice = "42800.00",
            closePrice = "43000.00",
            volume = "1200.00",
            closeTime = 1633049100000,
            quoteAssetVolume = "51840000.00",
            numberOfTrades = 1200,
            takerBuyBaseAssetVolume = "600.00",
            takerBuyQuoteAssetVolume = "25920000.00"
        )
    )

    UTXOTheme(LightColorScheme) {
        TickerCard(
            symbol = "BTCUSDT",
            price = "50000",
            timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString(),
            trades = sampleTrades,
            priceChangePercent = "-1.60"
        )
    }
}


class AppInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        ContextProvider.setContext(context.applicationContext)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

object ContextProvider {
    private lateinit var appContext: Context

    fun setContext(context: Context) {
        appContext = context
    }

    fun getContext(): Context {
        return appContext
    }
}
