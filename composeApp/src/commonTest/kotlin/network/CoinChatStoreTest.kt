package network

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import model.ChatRole
import model.CoinChatMessage

/**
 * Unit tests for [CoinChatStore] — the process-wide transcripts that let a user back out of a
 * coin's chat and come back to it. Coins must not bleed into each other, and a long session must
 * not grow this without bound.
 */
class CoinChatStoreTest {

    @BeforeTest
    fun setUp() = runTest { CoinChatStore.clearAll() }

    @AfterTest
    fun tearDown() = runTest { CoinChatStore.clearAll() }

    private fun messages(count: Int, prefix: String = "m") = (0 until count).map { index ->
        CoinChatMessage(
            id = index.toLong(),
            role = if (index % 2 == 0) ChatRole.User else ChatRole.Assistant,
            text = "$prefix$index"
        )
    }

    @Test
    fun roundTripsATranscript() = runTest {
        val transcript = messages(4)
        CoinChatStore.put("BTCUSDT", transcript)

        assertEquals(transcript, CoinChatStore.get("BTCUSDT"))
    }

    @Test
    fun returnsEmptyForACoinWithNoConversation() = runTest {
        assertTrue(CoinChatStore.get("ETHUSDT").isEmpty())
    }

    @Test
    fun coinsAreIsolatedFromEachOther() = runTest {
        CoinChatStore.put("BTCUSDT", messages(2, prefix = "btc"))
        CoinChatStore.put("ETHUSDT", messages(2, prefix = "eth"))

        assertEquals(listOf("btc0", "btc1"), CoinChatStore.get("BTCUSDT").map { it.text })
        assertEquals(listOf("eth0", "eth1"), CoinChatStore.get("ETHUSDT").map { it.text })
    }

    @Test
    fun trimsTheOldestTurnsPastTheMessageCap() = runTest {
        CoinChatStore.put("BTCUSDT", messages(CoinChatStore.MAX_MESSAGES + 4))

        val stored = CoinChatStore.get("BTCUSDT")

        assertEquals(CoinChatStore.MAX_MESSAGES, stored.size)
        // The newest survive; the oldest four are gone.
        assertEquals("m4", stored.first().text)
        assertEquals("m${CoinChatStore.MAX_MESSAGES + 3}", stored.last().text)
    }

    @Test
    fun evictsTheLeastRecentlyUsedConversation() = runTest {
        repeat(CoinChatStore.MAX_CONVERSATIONS) { index ->
            CoinChatStore.put("COIN$index", messages(2, prefix = "c$index-"))
        }
        // Touch the oldest so it is no longer the eviction candidate.
        CoinChatStore.get("COIN0")

        CoinChatStore.put("OVERFLOW", messages(2))

        assertTrue(CoinChatStore.get("COIN0").isNotEmpty(), "recently read conversation should survive")
        assertTrue(CoinChatStore.get("COIN1").isEmpty(), "least recently used conversation should be evicted")
        assertTrue(CoinChatStore.get("OVERFLOW").isNotEmpty())
    }

    @Test
    fun clearDropsOnlyThatCoin() = runTest {
        CoinChatStore.put("BTCUSDT", messages(2))
        CoinChatStore.put("ETHUSDT", messages(2))

        CoinChatStore.clear("BTCUSDT")

        assertTrue(CoinChatStore.get("BTCUSDT").isEmpty())
        assertTrue(CoinChatStore.get("ETHUSDT").isNotEmpty())
    }
}
