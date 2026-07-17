package network

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import model.NewsItem
import model.Ticker24hr

/**
 * Unit tests for [AiInsightService.buildUserPrompt] — the pure prompt builder that turns a
 * coin's 24h ticker and recent news into the message sent to the AI. These lock in the core
 * behaviour of this feature: news data actually reaches the model alongside the market data.
 */
class AiInsightPromptTest {

    private fun ticker() = Ticker24hr(
        symbol = "BTCUSDT",
        priceChange = "1200.0",
        priceChangePercent = "2.05",
        weightedAvgPrice = "59000.0",
        prevClosePrice = "58000.0",
        lastPrice = "60000.0",
        lastQty = "0.1",
        bidPrice = "59999.0",
        bidQty = "1.0",
        askPrice = "60001.0",
        askQty = "1.0",
        openPrice = "58800.0",
        highPrice = "60500.0",
        lowPrice = "58500.0",
        volume = "12345.0",
        quoteVolume = "700000000.0",
        openTime = 0L,
        closeTime = 0L
    )

    private fun news() = listOf(
        NewsItem(
            title = "Bitcoin  ETF   sees record inflows",
            description = "Spot   BTC funds\n absorbed money in a day.",
            link = "https://example.com/a",
            pubDate = "Wed, 16 Jul 2026 10:00:00 GMT",
            source = "CoinDesk"
        ),
        NewsItem(
            title = "Analysts eye BTC volatility",
            description = "Options desks report elevated demand.",
            link = "https://example.com/b",
            pubDate = "Wed, 16 Jul 2026 09:00:00 GMT",
            source = "The Block"
        )
    )

    @Test
    fun prompt_includes_both_market_data_and_news() {
        val prompt = AiInsightService.buildUserPrompt("BTCUSDT", "BTC", ticker(), news())

        // Market block is present.
        assertContains(prompt, "24h MARKET DATA")
        assertContains(prompt, "Last price: 60000.0")
        assertContains(prompt, "24h change: 2.05% (up)")

        // News block is present, each item labelled with its source, headline and snippet.
        assertContains(prompt, "RECENT NEWS")
        assertContains(prompt, "[CoinDesk] Bitcoin ETF sees record inflows") // whitespace collapsed
        assertContains(prompt, "[The Block] Analysts eye BTC volatility")

        // Newlines/extra whitespace inside descriptions are collapsed to single spaces.
        assertFalse(prompt.contains("\n absorbed"), "description newlines should be collapsed")
    }

    @Test
    fun prompt_falls_back_to_market_only_when_no_news() {
        val prompt = AiInsightService.buildUserPrompt("BTCUSDT", "BTC", ticker(), emptyList())

        assertContains(prompt, "no recent news is available")
        assertContains(prompt, "24h MARKET DATA")
        assertFalse(prompt.contains("RECENT NEWS"), "no news section when the list is empty")
    }

    @Test
    fun prompt_caps_the_number_of_news_items() {
        val many = (1..25).map {
            NewsItem(
                title = "Headline $it about BTC",
                description = "desc $it",
                link = "https://example.com/$it",
                pubDate = "",
                source = "U.Today"
            )
        }
        val prompt = AiInsightService.buildUserPrompt("BTCUSDT", "BTC", ticker(), many)

        // Only the first MAX_NEWS_ITEMS (10) headlines are included.
        assertContains(prompt, "Headline ${AiInsightService.MAX_NEWS_ITEMS} about BTC")
        assertFalse(
            prompt.contains("Headline ${AiInsightService.MAX_NEWS_ITEMS + 1} about BTC"),
            "news list should be capped at MAX_NEWS_ITEMS"
        )
    }
}
