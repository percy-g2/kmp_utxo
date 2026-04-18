package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewState

@Composable
actual fun TradingViewChart(
    symbol: String,
    interval: String,
    isDarkTheme: Boolean,
    modifier: Modifier
) {
    val url = buildTradingViewUrl(symbol, mapIntervalToTv(interval), isDarkTheme)
    val state = rememberWebViewState(url = url)
    state.webSettings.apply {
        supportZoom = false
        isJavaScriptEnabled = true
    }
    WebView(state = state, modifier = modifier)
}
