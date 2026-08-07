package network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.CoinChatMessage

/**
 * Process-wide transcripts for the per-coin AI chat, keyed by trading pair.
 *
 * `CoinChatViewModel` is scoped to its nav entry, so backing out of the chat and returning would
 * otherwise lose the conversation — which reads as a bug rather than as a feature. Keeping it here
 * makes leaving and re-entering a coin's chat resume where the user left off.
 *
 * In memory only, deliberately: the answers quote a price that was current when they were written,
 * so a transcript restored days later from disk would be quietly wrong. An app restart is a fine
 * place to draw the line, and it keeps chat text off the user's disk entirely.
 *
 * Lives outside the ViewModel for the same reason [AiInsightCache] does — both it and the ViewModel
 * are constructed per screen.
 */
internal object CoinChatStore {

    /** Bounded so a long browsing session can't grow this without limit. */
    const val MAX_CONVERSATIONS: Int = 10

    /**
     * Cap per coin. Only the newest [CoinChatService.MAX_HISTORY_MESSAGES] are ever sent to the
     * model, so this exists to bound memory, not the prompt — it is set well above the send window
     * so scrolling back through a long chat still works.
     */
    const val MAX_MESSAGES: Int = 60

    private val mutex = Mutex()

    /** Insertion-ordered, and re-inserted on read, so the first key is the least recently used. */
    private val conversations = LinkedHashMap<String, List<CoinChatMessage>>()

    suspend fun get(symbol: String): List<CoinChatMessage> = mutex.withLock {
        val existing = conversations[symbol] ?: return@withLock emptyList()
        // Re-insert to mark it most recently used.
        conversations.remove(symbol)
        conversations[symbol] = existing
        existing
    }

    /** Replaces the transcript for [symbol], trimming the oldest turns past [MAX_MESSAGES]. */
    suspend fun put(symbol: String, messages: List<CoinChatMessage>) {
        mutex.withLock {
            conversations.remove(symbol)
            conversations[symbol] = messages.takeLast(MAX_MESSAGES)
            while (conversations.size > MAX_CONVERSATIONS) {
                val leastRecentlyUsed = conversations.keys.firstOrNull() ?: break
                conversations.remove(leastRecentlyUsed)
            }
        }
    }

    /** Drops one coin's transcript — the screen's "clear conversation" action. */
    suspend fun clear(symbol: String) {
        mutex.withLock { conversations.remove(symbol) }
    }

    /** Drops everything. Used by tests to isolate cases against this shared object. */
    suspend fun clearAll() {
        mutex.withLock { conversations.clear() }
    }
}
