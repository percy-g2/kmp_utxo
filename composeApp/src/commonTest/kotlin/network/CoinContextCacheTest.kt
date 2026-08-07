package network

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import model.NewsItem
import model.Ticker24hr

/**
 * Unit tests for [CoinContextCache] — the handoff that lets the chat screen open with the market
 * data the coin screen already gathered, instead of re-requesting every RSS feed before it can
 * answer anything.
 */
class CoinContextCacheTest {

    private val t0 = 1_000_000L

    @BeforeTest
    fun setUp() = runTest { CoinContextCache.clear() }

    @AfterTest
    fun tearDown() = runTest { CoinContextCache.clear() }

    private fun ticker(lastPrice: String = "60000.0") = Ticker24hr(
        symbol = "BTCUSDT",
        priceChange = "1200.0",
        priceChangePercent = "2.05",
        weightedAvgPrice = "59000.0",
        prevClosePrice = "58000.0",
        lastPrice = lastPrice,
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
            title = "Headline",
            description = "Description",
            link = "https://example.com/a",
            pubDate = "Wed, 07 Aug 2026 12:00:00 GMT",
            source = "CoinDesk"
        )
    )

    private fun context() = CoinContextCache.CoinContext(ticker(), news(), overview = null)

    @Test
    fun returnsTheStoredContextWhileFresh() = runTest {
        CoinContextCache.put("BTCUSDT", context(), t0)

        val cached = CoinContextCache.get("BTCUSDT", t0 + CoinContextCache.TTL_MILLIS - 1)

        assertNotNull(cached)
        assertEquals("60000.0", cached.ticker?.lastPrice)
        assertEquals(1, cached.news.size)
    }

    @Test
    fun missesOnceTheEntryReachesTheTtl() = runTest {
        CoinContextCache.put("BTCUSDT", context(), t0)

        assertNull(CoinContextCache.get("BTCUSDT", t0 + CoinContextCache.TTL_MILLIS))
    }

    @Test
    fun treatsABackwardsClockAsStaleRatherThanAsNeverExpiring() = runTest {
        CoinContextCache.put("BTCUSDT", context(), t0)

        assertNull(CoinContextCache.get("BTCUSDT", t0 - 1))
    }

    @Test
    fun overviewIsAttachedWithoutDisturbingTheRest() = runTest {
        CoinContextCache.put("BTCUSDT", context(), t0)

        CoinContextCache.putOverview("BTCUSDT", "Bitcoin is holding near its 24h high.")

        val cached = CoinContextCache.get("BTCUSDT", t0 + 1)
        assertNotNull(cached)
        assertEquals("Bitcoin is holding near its 24h high.", cached.overview)
        assertEquals("60000.0", cached.ticker?.lastPrice)
        assertEquals(1, cached.news.size)
    }

    @Test
    fun attachingAnOverviewToAnUnknownCoinIsANoOp() = runTest {
        CoinContextCache.putOverview("BTCUSDT", "overview")

        assertNull(CoinContextCache.get("BTCUSDT", t0))
    }

    @Test
    fun evictsTheLeastRecentlyUsedEntry() = runTest {
        repeat(CoinContextCache.MAX_ENTRIES) { index ->
            CoinContextCache.put("COIN$index", context(), t0)
        }
        // Touch the oldest so it is no longer the eviction candidate.
        CoinContextCache.get("COIN0", t0)

        CoinContextCache.put("OVERFLOW", context(), t0)

        assertNotNull(CoinContextCache.get("COIN0", t0), "recently read entry should survive")
        assertNull(CoinContextCache.get("COIN1", t0), "least recently used entry should be evicted")
    }
}
