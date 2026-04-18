package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun TradingViewChart(
    symbol: String,
    interval: String,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
)

internal fun mapIntervalToTv(interval: String): String = when (interval) {
    "1m" -> "1"
    "5m" -> "5"
    "15m" -> "15"
    "1h" -> "60"
    "4h" -> "240"
    "1d" -> "D"
    else -> "60"
}

internal fun buildTradingViewUrl(
    symbol: String,
    tvInterval: String,
    isDarkTheme: Boolean
): String {
    val theme = if (isDarkTheme) "dark" else "light"
    return "https://s.tradingview.com/widgetembed/" +
        "?symbol=BINANCE:$symbol" +
        "&interval=$tvInterval" +
        "&theme=$theme" +
        "&style=1" +
        "&locale=en" +
        "&timezone=Etc/UTC" +
        "&hidesidetoolbar=0" +
        "&symboledit=0" +
        "&saveimage=0" +
        "&withdateranges=1"
}
