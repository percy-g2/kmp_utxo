package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView

@Composable
actual fun TradingViewChart(
    symbol: String,
    interval: String,
    isDarkTheme: Boolean,
    modifier: Modifier
) {
    // Start JavaFX toolkit exactly once for the JVM process.
    remember { JavaFxBootstrap.ensureStarted() }

    val currentSymbol by rememberUpdatedState(symbol)
    val currentInterval by rememberUpdatedState(interval)
    val currentDark by rememberUpdatedState(isDarkTheme)

    var holder by remember { mutableStateOf<WebViewHolder?>(null) }

    LaunchedEffect(symbol, interval, isDarkTheme) {
        val html = buildTradingViewUrl(currentSymbol, mapIntervalToTv(currentInterval), currentDark)
        val h = holder
        if (h != null) {
            Platform.runLater { h.webView.engine.load(html) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            holder?.let { h ->
                Platform.runLater {
                    runCatching { h.webView.engine.load(null) }
                }
            }
        }
    }

    SwingPanel(
        modifier = modifier,
        factory = {
            val panel = JFXPanel()
            Platform.runLater {
                val webView = WebView()
                webView.isContextMenuEnabled = false
                val scene = Scene(webView)
                panel.scene = scene
                webView.engine.load(
                    buildTradingViewUrl(currentSymbol, mapIntervalToTv(currentInterval), currentDark)
                )
                holder = WebViewHolder(webView)
            }
            panel
        }
    )
}

private class WebViewHolder(val webView: WebView)

private object JavaFxBootstrap {
    @Volatile private var started = false
    fun ensureStarted() {
        if (started) return
        synchronized(this) {
            if (started) return
            // Instantiating JFXPanel starts the JavaFX toolkit.
            JFXPanel()
            Platform.setImplicitExit(false)
            started = true
        }
    }
}
