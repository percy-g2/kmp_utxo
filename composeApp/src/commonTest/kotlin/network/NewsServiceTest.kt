package network

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [NewsService.parseRSSFeed], the regex RSS reader behind the news list.
 *
 * The point these pin down is the distinction [NewsFetchResult] exists for: a feed that parses to
 * zero items is a perfectly good [NewsFetchResult.Success], not a failure. Conflating the two is
 * what let every RSS request on the web build 401 for weeks while the UI calmly reported
 * "No news available for this coin".
 */
class NewsServiceTest {

    private val service = NewsService()

    // NewsService builds a real Ktor client in its constructor; release it so the suite does not
    // leak one engine per test method.
    @AfterTest
    fun tearDown() = service.close()

    private fun item(
        title: String,
        description: String,
        link: String,
        pubDate: String = "Mon, 01 Sep 2025 10:00:00 +0000"
    ) = """
        <item>
          <title>$title</title>
          <description>$description</description>
          <link>$link</link>
          <pubDate>$pubDate</pubDate>
        </item>
    """.trimIndent()

    private fun feed(vararg items: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
          <title>Example Feed</title>
          ${items.joinToString("\n")}
        </channel></rss>
    """.trimIndent()

    @Test
    fun keepsOnlyItemsMentioningTheCoin() {
        val xml = feed(
            item("Bitcoin rallies past resistance", "BTC momentum builds", "https://www.coindesk.com/a"),
            item("Ethereum devs ship upgrade", "Staking changes land", "https://www.coindesk.com/b")
        )

        val result = service.parseRSSFeed(xml, "BTCUSDT")

        assertEquals(1, result.size)
        assertEquals("Bitcoin rallies past resistance", result[0].title)
        assertEquals("CoinDesk", result[0].source)
        assertEquals("Mon, 01 Sep 2025 10:00:00 +0000", result[0].pubDate)
    }

    @Test
    fun wellFormedFeedWithNoMentionsParsesToEmptyRatherThanFailing() {
        // The genuine "nothing about this coin today" case. It must stay distinguishable from a
        // transport failure, which never reaches this function at all.
        val xml = feed(item("Solana NFT volumes climb", "Marketplace activity up", "https://decrypt.co/x"))

        assertTrue(service.parseRSSFeed(xml, "BTCUSDT").isEmpty())
    }

    @Test
    fun nonFeedBodiesYieldNoItems() {
        assertTrue(service.parseRSSFeed("", "BTCUSDT").isEmpty())
        assertTrue(service.parseRSSFeed("<html><body>403 Forbidden</body></html>", "BTCUSDT").isEmpty())
        // The literal body corsproxy.io started returning once it went commercial.
        assertTrue(
            service.parseRSSFeed("""{"error":"A valid API key is required."}""", "BTCUSDT").isEmpty()
        )
    }

    @Test
    fun stripsQuoteCurrencyBeforeMatching() {
        val xml = feed(item("Solana ecosystem grows", "SOL activity climbs", "https://decrypt.co/s"))

        assertEquals(1, service.parseRSSFeed(xml, "SOLUSDT").size)
        assertEquals(1, service.parseRSSFeed(xml, "SOLBTC").size)
        assertTrue(service.parseRSSFeed(xml, "ADAUSDT").isEmpty())
    }

    @Test
    fun matchesOnCoinNameNotJustTicker() {
        val xml = feed(item("Cardano rolls out governance", "No ticker in sight", "https://u.today/z"))

        assertEquals(1, service.parseRSSFeed(xml, "ADAUSDT").size)
    }

    @Test
    fun cleansCdataMarkupAndEntitiesOutOfTitles() {
        val xml = feed(
            item(
                "<![CDATA[Bitcoin&#8217;s <b>best</b> week]]>",
                "BTC gains",
                "https://cointelegraph.com/news/x"
            )
        )

        val result = service.parseRSSFeed(xml, "BTCUSDT")

        assertEquals(1, result.size)
        assertEquals("Bitcoin’s best week", result[0].title)
        assertEquals("CoinTelegraph", result[0].source)
    }

    @Test
    fun fallsBackToGenericSourceForUnknownDomains() {
        val xml = feed(item("Bitcoin update", "BTC news", "https://example.com/post"))

        assertEquals("Crypto News", service.parseRSSFeed(xml, "BTCUSDT").single().source)
    }
}
