@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.browser.document
import org.w3c.dom.HTMLIFrameElement

@Composable
actual fun TradingViewChart(
    symbol: String,
    interval: String,
    isDarkTheme: Boolean,
    modifier: Modifier
) {
    val density = LocalDensity.current.density
    val iframe = remember { document.createElement("iframe") as HTMLIFrameElement }

    DisposableEffect(Unit) {
        iframe.style.apply {
            position = "fixed"
            border = "0"
            margin = "0"
            padding = "0"
            left = "0px"
            top = "0px"
            width = "0px"
            height = "0px"
            setProperty("z-index", "2147483647")
            backgroundColor = "transparent"
        }
        iframe.setAttribute("allow", "fullscreen")
        iframe.setAttribute("allowfullscreen", "true")
        // Compose Multiplatform attaches a shadow root to <body>; light-DOM children of
        // body don't render. Attach the iframe to <html> so it sits outside the shadow host.
        (document.documentElement ?: document.body)?.appendChild(iframe)
        onDispose { iframe.remove() }
    }

    LaunchedEffect(symbol, interval, isDarkTheme) {
        iframe.src = buildTradingViewUrl(symbol, mapIntervalToTv(interval), isDarkTheme)
    }

    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            val pos = coords.positionInWindow()
            val size = coords.size
            iframe.style.left = "${pos.x / density}px"
            iframe.style.top = "${pos.y / density}px"
            iframe.style.width = "${size.width / density}px"
            iframe.style.height = "${size.height / density}px"
        }
    )
}
