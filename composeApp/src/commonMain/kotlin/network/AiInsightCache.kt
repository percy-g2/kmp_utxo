package network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide cache of generated overviews, keyed by trading pair.
 *
 * `CoinDetailViewModel.loadCoinData` runs on every visit to a coin screen, so without this, browsing
 * between coins spends one llm7.io request per visit — and anonymous callers only get 10 a minute.
 *
 * An entry is reused while it is younger than [TTL_MILLIS] and the headline set behind it is
 * unchanged. The 24h ticker is deliberately *not* part of the key: the overview is qualitative
 * ("near the 24-hour high", "robust trading activity"), so a few minutes of price drift doesn't
 * change what it says, and [AiInsightService.buildUserPrompt] embeds `lastPrice` at full precision —
 * keying on it would mean never hitting the cache on a liquid pair.
 *
 * Deliberate user actions — the card's Retry, or saving an API token — bypass this entirely.
 *
 * Lives outside the ViewModel because both it and [AiInsightService] are constructed per screen.
 */
internal object AiInsightCache {

    /** How long an overview stays presentable as current. */
    const val TTL_MILLIS: Long = 10 * 60 * 1000L

    /** Bounded so a long browsing session can't grow this without limit. */
    const val MAX_ENTRIES: Int = 20

    private data class Entry(
        val text: String,
        val newsFingerprint: String,
        val storedAtMillis: Long
    )

    private val mutex = Mutex()

    /** Insertion-ordered, and re-inserted on read, so the first key is the least recently used. */
    private val entries = LinkedHashMap<String, Entry>()

    /**
     * Returns a still-valid overview for [symbol], or null when there is none — which also drops the
     * stale entry. [nowMillis] is passed in rather than read here so this stays directly testable.
     */
    suspend fun get(symbol: String, newsFingerprint: String, nowMillis: Long): String? = mutex.withLock {
        val entry = entries[symbol] ?: return@withLock null

        val age = nowMillis - entry.storedAtMillis
        // A negative age means the clock moved backwards; treat that as stale rather than as an
        // entry that never expires.
        if (entry.newsFingerprint != newsFingerprint || age !in 0 until TTL_MILLIS) {
            entries.remove(symbol)
            return@withLock null
        }

        // Re-insert to mark it most recently used.
        entries.remove(symbol)
        entries[symbol] = entry
        entry.text
    }

    suspend fun put(symbol: String, newsFingerprint: String, text: String, nowMillis: Long) {
        mutex.withLock {
            entries.remove(symbol)
            entries[symbol] = Entry(text, newsFingerprint, nowMillis)
            while (entries.size > MAX_ENTRIES) {
                val leastRecentlyUsed = entries.keys.firstOrNull() ?: break
                entries.remove(leastRecentlyUsed)
            }
        }
    }

    /** Drops everything. Used by tests to isolate cases against this shared object. */
    suspend fun clear() {
        mutex.withLock { entries.clear() }
    }
}
