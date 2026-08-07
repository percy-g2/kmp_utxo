package network

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import model.NewsItem

/**
 * Unit tests for [AiInsightCache] and the fingerprint it is keyed on.
 *
 * These lock in the point of the cache: revisiting a coin inside the TTL must not spend another
 * llm7.io request, while genuinely new headlines — or an entry that has aged out — must.
 */
class AiInsightCacheTest {

    private val t0 = 1_000_000L

    @BeforeTest
    fun setUp() = runTest { AiInsightCache.clear() }

    @AfterTest
    fun tearDown() = runTest { AiInsightCache.clear() }

    private fun newsItem(link: String, title: String = "Headline") = NewsItem(
        title = title,
        description = "Description",
        link = link,
        pubDate = "Wed, 07 Aug 2026 12:00:00 GMT",
        source = "CoinTelegraph"
    )

    @Test
    fun returnsStoredTextWhileFreshAndFingerprintMatches() = runTest {
        AiInsightCache.put("BTCUSDT", "news-a", "Bitcoin is holding near its 24h high.", t0)

        assertEquals(
            "Bitcoin is holding near its 24h high.",
            AiInsightCache.get("BTCUSDT", "news-a", t0 + AiInsightCache.TTL_MILLIS - 1)
        )
    }

    @Test
    fun missesOnceTheEntryReachesTheTtl() = runTest {
        AiInsightCache.put("BTCUSDT", "news-a", "overview", t0)

        assertNull(AiInsightCache.get("BTCUSDT", "news-a", t0 + AiInsightCache.TTL_MILLIS))
    }

    @Test
    fun missesWhenTheHeadlineSetChanged() = runTest {
        AiInsightCache.put("BTCUSDT", "news-a", "overview", t0)

        assertNull(AiInsightCache.get("BTCUSDT", "news-b", t0 + 1))
    }

    /** A backwards clock jump must expire the entry, not make it live forever. */
    @Test
    fun missesWhenTheClockMovedBackwards() = runTest {
        AiInsightCache.put("BTCUSDT", "news-a", "overview", t0)

        assertNull(AiInsightCache.get("BTCUSDT", "news-a", t0 - 1))
    }

    @Test
    fun keepsSymbolsIndependent() = runTest {
        AiInsightCache.put("BTCUSDT", "news-a", "bitcoin overview", t0)
        AiInsightCache.put("ETHUSDT", "news-b", "ether overview", t0)

        assertEquals("bitcoin overview", AiInsightCache.get("BTCUSDT", "news-a", t0 + 1))
        assertEquals("ether overview", AiInsightCache.get("ETHUSDT", "news-b", t0 + 1))
    }

    @Test
    fun evictsTheLeastRecentlyUsedOnceFull() = runTest {
        repeat(AiInsightCache.MAX_ENTRIES) { i ->
            AiInsightCache.put("PAIR$i", "news", "overview $i", t0)
        }
        // Touch the oldest so it is no longer the least recently used.
        assertEquals("overview 0", AiInsightCache.get("PAIR0", "news", t0 + 1))

        AiInsightCache.put("NEWPAIR", "news", "newest", t0 + 2)

        assertEquals("overview 0", AiInsightCache.get("PAIR0", "news", t0 + 3))
        assertEquals("newest", AiInsightCache.get("NEWPAIR", "news", t0 + 3))
        assertNull(AiInsightCache.get("PAIR1", "news", t0 + 3))
    }

    @Test
    fun overwritingRefreshesTheEntryAge() = runTest {
        AiInsightCache.put("BTCUSDT", "news-a", "first", t0)
        AiInsightCache.put("BTCUSDT", "news-a", "second", t0 + AiInsightCache.TTL_MILLIS)

        assertEquals(
            "second",
            AiInsightCache.get("BTCUSDT", "news-a", t0 + AiInsightCache.TTL_MILLIS + 1)
        )
    }

    @Test
    fun fingerprintTracksTheHeadlinesThatReachTheModel() {
        val a = listOf(newsItem("https://example.com/1"), newsItem("https://example.com/2"))
        val b = listOf(newsItem("https://example.com/1"), newsItem("https://example.com/3"))

        assertEquals(AiInsightService.newsFingerprint(a), AiInsightService.newsFingerprint(a))
        assertEquals(false, AiInsightService.newsFingerprint(a) == AiInsightService.newsFingerprint(b))
    }

    /**
     * The news list is built by merging providers that finish in a nondeterministic order, and
     * `sortedWith` is stable — so two loads of the same stories can order same-timestamp headlines
     * differently. The fingerprint must not treat that as a change, or the cache never hits.
     */
    @Test
    fun fingerprintIgnoresHeadlineOrder() {
        val a = listOf(newsItem("https://example.com/1"), newsItem("https://example.com/2"))
        val reordered = a.reversed()

        assertEquals(
            AiInsightService.newsFingerprint(a),
            AiInsightService.newsFingerprint(reordered)
        )
    }

    /** Only the first [AiInsightService.MAX_NEWS_ITEMS] reach the prompt, so only they may key it. */
    @Test
    fun fingerprintIgnoresHeadlinesBeyondThePromptCap() {
        val capped = List(AiInsightService.MAX_NEWS_ITEMS) { newsItem("https://example.com/$it") }
        val extra = capped + newsItem("https://example.com/never-sent")

        assertEquals(
            AiInsightService.newsFingerprint(capped),
            AiInsightService.newsFingerprint(extra)
        )
    }

    /** Feeds that omit a link must still produce distinct fingerprints for distinct stories. */
    @Test
    fun fingerprintFallsBackToTheTitleWhenALinkIsMissing() {
        val a = listOf(newsItem(link = "", title = "MARA sells reserves"))
        val b = listOf(newsItem(link = "", title = "Saylor on the Clarity Act"))

        assertEquals(false, AiInsightService.newsFingerprint(a) == AiInsightService.newsFingerprint(b))
    }

    @Test
    fun emptyNewsIsAStableFingerprint() {
        assertEquals(
            AiInsightService.newsFingerprint(emptyList()),
            AiInsightService.newsFingerprint(emptyList())
        )
    }
}
