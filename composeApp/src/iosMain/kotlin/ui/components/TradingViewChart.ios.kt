package ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.rememberWebViewNavigator
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
    val navigator = rememberWebViewNavigator()
    state.webSettings.apply {
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow
        supportZoom = false
        isJavaScriptEnabled = true
    }
    LaunchedEffect(state.loadingState, isDarkTheme) {
        if (state.loadingState is LoadingState.Finished) {
            navigator.evaluateJavaScript(buildTradingViewChromeScript(isDarkTheme))
        }
    }
    WebView(state = state, navigator = navigator, modifier = modifier)
}
