package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import io.ktor.http.encodeURLParameter
import theme.DarkColorScheme
import theme.LightColorScheme

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
    val colors = if (isDarkTheme) DarkColorScheme else LightColorScheme
    // The embed does not inherit Compose colors. Explicitly style its canvas in both themes.
    val background = "#" + colors.surfaceContainerLow.toArgb().toUInt().toString(16).takeLast(6)
    val grid = "#" + colors.outlineVariant.toArgb().toUInt().toString(16).takeLast(6)
    return "https://s.tradingview.com/widgetembed/" +
        "?symbol=BINANCE:$symbol" +
        "&interval=$tvInterval" +
        "&theme=$theme" +
        "&backgroundColor=${background.encodeURLParameter()}" +
        "&gridColor=${grid.encodeURLParameter()}" +
        "&style=1" +
        "&locale=en" +
        "&timezone=Etc/UTC" +
        "&hidesidetoolbar=0" +
        "&symboledit=0" +
        "&saveimage=0" +
        "&withdateranges=1"
}

/** Styles native WebView chrome, which the embed's backgroundColor only applies to the plot. */
internal fun buildTradingViewChromeScript(isDarkTheme: Boolean): String {
    val colors = if (isDarkTheme) DarkColorScheme else LightColorScheme
    val background = "#" + colors.surfaceContainerLow.toArgb().toUInt().toString(16).takeLast(6)
    return """
        (function() {
            var style = document.getElementById('utxo-chart-theme');
            if (!style) {
                style = document.createElement('style');
                style.id = 'utxo-chart-theme';
                document.head.appendChild(style);
            }
            style.textContent = `
                :root, [data-theme] {
                    --tv-color-platform-background: $background !important;
                    --tv-color-pane-background: $background !important;
                    --color-header-bg: $background !important;
                    --color-body-bg: $background !important;
                    --color-bg-primary: $background !important;
                    --color-bg-secondary: $background !important;
                    --color-pane-bg: $background !important;
                    --color-chart-page-bg: $background !important;
                }
            `;
        })();
    """.trimIndent()
}
