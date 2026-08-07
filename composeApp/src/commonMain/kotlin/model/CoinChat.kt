package model

/**
 * UI-facing models for the per-coin AI chat.
 *
 * Deliberately separate from the OpenAI wire DTOs in [ChatMessage]: those carry a free-form `role`
 * string because that is what the API contract says, while the UI only ever renders two kinds of
 * bubble and needs a stable list key. [network.CoinChatService] maps between the two.
 */
enum class ChatRole { User, Assistant }

data class CoinChatMessage(
    /**
     * Stable key for `LazyColumn`. Transcripts are append-only, so the next id is always
     * `messages.lastOrNull()?.id?.plus(1) ?: 0L` — no UUID source is needed in commonMain, and
     * ids stay reproducible for tests.
     */
    val id: Long,
    val role: ChatRole,
    val text: String
)
