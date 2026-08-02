package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible chat DTOs used to talk to the AI Insights gateway
 * (`POST https://api.llm7.io/v1/chat/completions`).
 *
 * The shapes are the standard OpenAI chat-completions contract, so the AI
 * provider can be swapped by editing only [network.AiInsightService.AiConfig]
 * without touching these models.
 */
@Serializable
data class ChatMessage(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String
)

@Serializable
data class ChatCompletionRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<ChatMessage>,
    @SerialName("temperature") val temperature: Double = 0.4,
    @SerialName("stream") val stream: Boolean = false
    // Deliberately no `max_tokens`: gpt-oss:20b is a reasoning model that spends completion tokens
    // thinking before it answers. Capping the budget makes it exhaust the cap on reasoning and
    // return `finish_reason: length` with an EMPTY content, which the UI would show as an error.
    // Length is controlled by the system prompt ("under 120 words") instead.
)

@Serializable
data class ChatCompletionResponse(
    @SerialName("choices") val choices: List<ChatChoice> = emptyList()
)

@Serializable
data class ChatChoice(
    @SerialName("message") val message: ChatMessage? = null
)
