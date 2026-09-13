package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import kotlin.test.assertEquals
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import java.io.File
import model.Ticker24hr
import model.TradingPair
import org.jetbrains.skia.EncodedImageFormat
import theme.UTXOTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Offscreen renders of the real components, using fixed sample data and no network services. */
class CoinDetailRenderTest {
    private val ticker = Ticker24hr(
        symbol = "BTCUSDT", priceChange = "1234.56", priceChangePercent = "2.34",
        weightedAvgPrice = "67200.00", prevClosePrice = "66000.00", lastPrice = "67890.12",
        lastQty = "0.01", bidPrice = "67889.00", bidQty = "1.2", askPrice = "67891.00", askQty = "0.8",
        openPrice = "66000.00", highPrice = "68500.00", lowPrice = "65200.00", volume = "19234.5",
        quoteVolume = "1298765432", openTime = 0, closeTime = 0,
    )
    private val pairs = listOf(TradingPair("1", "BTCUSDT", "BTC", "USDT", true, true, true, "TRADING"))

    @Test
    fun renderPhoneThemesAndLargeText() {
        listOf(Triple("light", false, 1f), Triple("dark", true, 1f), Triple("large-text", false, 1.5f), Triple("narrow", false, 1.5f))
            .forEach { (name, dark, fontScale) ->
                ImageComposeScene(width = if (name == "narrow") 320 else 390, height = 844, density = Density(1f, fontScale)) {
                    UTXOTheme(darkTheme = dark) {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Column {
                                CoinPriceHero("BTCUSDT", ticker, false, dark, pairs, {})
                                CoinDetailTabs(0, {})
                                TimeframeSelector("1h", {})
                                PriceInfoSection("BTCUSDT", ticker, dark, pairs)
                            }
                        }
                    }
                }.use { scene ->
                    scene.render().close()
                    fun checkText(node: SemanticsNode) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                        layouts.forEach { layout ->
                            // Skia rounds glyph widths to integer pixels; allow that rounding only.
                            assertTrue(
                                (0 until layout.lineCount).all { line ->
                                    layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f
                                } &&
                                    layout.multiParagraph.height <= layout.size.height + 1f &&
                                    !layout.multiParagraph.didExceedMaxLines,
                                "Clipped text in $name: ${layout.layoutInput.text}",
                            )
                        }
                        node.children.forEach(::checkText)
                    }
                    scene.semanticsOwners.forEach { checkText(it.rootSemanticsNode) }
                    scene.render().use { image ->
                        val bytes = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
                        assertTrue(bytes.isNotEmpty())
                        File("build/reports/ui/coin-detail-$name.png").apply {
                            parentFile.mkdirs()
                            writeBytes(bytes)
                        }
                    }
                }
            }
    }
    @Test
    fun tabsExposeSelectionAndDispatchNavigation() {
        val selected = mutableStateOf(0)
        ImageComposeScene(390, 100) {
            UTXOTheme(darkTheme = false) {
                CoinDetailTabs(selected.value) { selected.value = it }
            }
        }.use { scene ->
            scene.render().close()
            fun tabs(node: SemanticsNode): List<SemanticsNode> =
                (if (node.config.getOrNull(SemanticsProperties.Role) == Role.Tab) listOf(node) else emptyList()) +
                    node.children.flatMap(::tabs)
            val nodes = scene.semanticsOwners.flatMap { tabs(it.rootSemanticsNode) }
            assertEquals(4, nodes.size)
            assertEquals(true, nodes[0].config.getOrNull(SemanticsProperties.Selected))
            nodes[2].config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
            assertEquals(2, selected.value)
            scene.render().close()
            val updated = scene.semanticsOwners.flatMap { tabs(it.rootSemanticsNode) }
            assertEquals(true, updated[2].config.getOrNull(SemanticsProperties.Selected))
            assertEquals(false, updated[0].config.getOrNull(SemanticsProperties.Selected))
        }
    }

}
