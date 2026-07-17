package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible chat DTOs used to talk to Pollinations' text endpoint
 * (`POST https://text.pollinations.ai/openai`).
 *
 * The shapes are the standard OpenAI chat-completions contract, so the AI
 * provider can be swapped by editing only [network.AiInsightService.AiConfig]
 * (e.g. point at a self-hosted Pollinations instance or any other
 * OpenAI-compatible gateway) without touching these models.
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
    @SerialName("max_tokens") val maxTokens: Int = 420,
    @SerialName("stream") val stream: Boolean = false
)

@Serializable
data class ChatCompletionResponse(
    @SerialName("choices") val choices: List<ChatChoice> = emptyList()
)

@Serializable
data class ChatChoice(
    @SerialName("message") val message: ChatMessage? = null
)
