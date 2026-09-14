package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLIFrameElement

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun TradingViewChart(
    symbol: String,
    interval: String,
    isDarkTheme: Boolean,
    modifier: Modifier
) {
    val chartUrl = buildTradingViewUrl(symbol, mapIntervalToTv(interval), isDarkTheme)

    // Let Compose own the DOM element's placement, clipping and lifecycle. A fixed
    // iframe attached to <html> escapes the scroll viewport and overlays app controls.
    HtmlElementView(
        factory = {
            (document.createElement("iframe") as HTMLIFrameElement).apply {
                style.border = "0"
                style.backgroundColor = "transparent"
                setAttribute("allow", "fullscreen")
                setAttribute("allowfullscreen", "true")
            }
        },
        modifier = modifier,
        update = { iframe ->
            // Ticker updates must not reload the chart and reset its zoom/position.
            if (iframe.getAttribute("src") != chartUrl) {
                iframe.src = chartUrl
            }
        },
    )
}
