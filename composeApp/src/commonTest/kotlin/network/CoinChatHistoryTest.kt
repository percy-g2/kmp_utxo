package network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import model.ChatRole
import model.CoinChatMessage

/**
 * Unit tests for [CoinChatService.trimHistory] — the window of prior turns that travels with each
 * question. Every request already carries the full market and news context, so this is what keeps
 * a long conversation from growing the prompt without bound.
 */
class CoinChatHistoryTest {

    /** Alternating user/assistant turns, starting on a user turn, as a real transcript does. */
    private fun transcript(size: Int) = (0 until size).map { index ->
        CoinChatMessage(
            id = index.toLong(),
            role = if (index % 2 == 0) ChatRole.User else ChatRole.Assistant,
            text = "message $index"
        )
    }

    @Test
    fun shortHistoryPassesThroughUnchanged() {
        val history = transcript(4)

        assertEquals(history, CoinChatService.trimHistory(history, maxMessages = 12))
    }

    @Test
    fun keepsTheNewestTurns() {
        val history = transcript(20)

        val trimmed = CoinChatService.trimHistory(history, maxMessages = 6)

        assertEquals(listOf("message 14", "message 15", "message 16", "message 17", "message 18", "message 19"), trimmed.map { it.text })
    }

    @Test
    fun dropsAnAssistantTurnLeftDanglingByTheCut() {
        // Taking the last 5 of an alternating transcript starts on message 15 — an assistant turn
        // answering a question the model can no longer see.
        val trimmed = CoinChatService.trimHistory(transcript(20), maxMessages = 5)

        assertEquals(ChatRole.User, trimmed.first().role)
        assertEquals("message 16", trimmed.first().text)
        assertEquals(4, trimmed.size)
    }

    @Test
    fun aHistoryOfNothingButAnswersIsDroppedEntirely() {
        val history = listOf(
            CoinChatMessage(0L, ChatRole.Assistant, "first"),
            CoinChatMessage(1L, ChatRole.Assistant, "second")
        )

        assertTrue(CoinChatService.trimHistory(history).isEmpty())
    }

    @Test
    fun handlesEmptyAndDegenerateWindows() {
        assertTrue(CoinChatService.trimHistory(emptyList()).isEmpty())
        assertTrue(CoinChatService.trimHistory(transcript(10), maxMessages = 0).isEmpty())
        assertTrue(CoinChatService.trimHistory(transcript(10), maxMessages = -3).isEmpty())
    }
}
