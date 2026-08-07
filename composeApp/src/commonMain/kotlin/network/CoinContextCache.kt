package network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.NewsItem
import model.Ticker24hr

/**
 * Hands the market context a coin screen already gathered — 24h ticker, news headlines and the
 * generated overview — across to the chat screen.
 *
 * The chat can't simply read `CoinDetailViewModel`: on the iOS 26 native tab bar every screen is
 * its own `ComposeUIViewController` with no shared back stack, so there is no ViewModel to reach.
 * Re-fetching instead would be worse than it looks — [NewsService] holds a cache field it never
 * actually reads, so a fresh instance re-hits every RSS feed over the network, adding seconds
 * before the first question can be answered.
 *
 * A miss is not an error: [ui.CoinChatViewModel] falls back to fetching its own context, which is
 * what happens when the chat is opened without visiting the coin screen first.
 *
 * Same discipline as [AiInsightCache]: mutex-guarded, LRU-bounded, TTL'd, and [nowMillis] is passed
 * in rather than read here so this stays directly testable.
 */
internal object CoinContextCache {

    /** Matches [AiInsightCache.TTL_MILLIS] — the two describe the same snapshot of a coin. */
    const val TTL_MILLIS: Long = 10 * 60 * 1000L

    const val MAX_ENTRIES: Int = 10

    data class CoinContext(
        val ticker: Ticker24hr?,
        val news: List<NewsItem>,
        val overview: String?
    )

    private data class Entry(
        val context: CoinContext,
        val storedAtMillis: Long
    )

    private val mutex = Mutex()

    /** Insertion-ordered, and re-inserted on read, so the first key is the least recently used. */
    private val entries = LinkedHashMap<String, Entry>()

    suspend fun get(symbol: String, nowMillis: Long): CoinContext? = mutex.withLock {
        val entry = entries[symbol] ?: return@withLock null

        val age = nowMillis - entry.storedAtMillis
        // A negative age means the clock moved backwards; treat that as stale rather than as an
        // entry that never expires.
        if (age !in 0 until TTL_MILLIS) {
            entries.remove(symbol)
            return@withLock null
        }

        entries.remove(symbol)
        entries[symbol] = entry
        entry.context
    }

    suspend fun put(symbol: String, context: CoinContext, nowMillis: Long) {
        mutex.withLock {
            entries.remove(symbol)
            entries[symbol] = Entry(context, nowMillis)
            while (entries.size > MAX_ENTRIES) {
                val leastRecentlyUsed = entries.keys.firstOrNull() ?: break
                entries.remove(leastRecentlyUsed)
            }
        }
    }

    /**
     * Attaches a freshly generated overview to the entry for [symbol] without disturbing the
     * ticker and news already stored, and without resetting its age.
     *
     * The overview lands a few seconds after the market data it describes, so the coin screen
     * publishes twice; re-storing the whole context on the second write would need the caller to
     * re-thread the news list it no longer holds.
     */
    suspend fun putOverview(symbol: String, overview: String) {
        mutex.withLock {
            val entry = entries[symbol] ?: return@withLock
            entries[symbol] = entry.copy(context = entry.context.copy(overview = overview))
        }
    }

    /** Drops everything. Used by tests to isolate cases against this shared object. */
    suspend fun clear() {
        mutex.withLock { entries.clear() }
    }
}
