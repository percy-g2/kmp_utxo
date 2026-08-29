package network

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logging.AppLogger
import model.ChatCompletionRequest
import model.ChatMessage

/**
 * The raw response plus a body already consumed while deciding whether a fallback was needed.
 * Ktor response bodies are streams, so callers must reuse [errorBody] instead of reading twice.
 */
internal data class AiCompletionHttpResult(
    val response: HttpResponse,
    val errorBody: String? = null
)

/**
 * Sends a completion through llm7 and retries once with its provider-managed selector when the
 * preferred deployment is unavailable. The selector keeps model discovery/routing on the gateway;
 * choosing an arbitrary entry from `/models` here could silently select a paid model.
 */
internal suspend fun HttpClient.postAiCompletion(
    messages: List<ChatMessage>,
    apiToken: String
): AiCompletionHttpResult {
    val primaryResponse = postAiCompletion(
        model = AiInsightService.AiConfig.MODEL,
        messages = messages,
        apiToken = apiToken
    )

    if (primaryResponse.status != HttpStatusCode.BadRequest) {
        return AiCompletionHttpResult(primaryResponse)
    }

    val primaryErrorBody = primaryResponse.bodyAsText()
    if (!isModelUnavailableError(primaryErrorBody)) {
        return AiCompletionHttpResult(primaryResponse, primaryErrorBody)
    }

    AppLogger.logger.w {
        "AI model ${AiInsightService.AiConfig.MODEL} unavailable; retrying with " +
            AiInsightService.AiConfig.FALLBACK_MODEL
    }
    return AiCompletionHttpResult(
        response = postAiCompletion(
            model = AiInsightService.AiConfig.FALLBACK_MODEL,
            messages = messages,
            apiToken = apiToken
        )
    )
}

private suspend fun HttpClient.postAiCompletion(
    model: String,
    messages: List<ChatMessage>,
    apiToken: String
): HttpResponse = post(AiInsightService.AiConfig.ENDPOINT) {
    contentType(ContentType.Application.Json)
    // Optional: raises the rate limits. Omitted entirely when blank, which llm7.io serves
    // anonymously rather than rejecting.
    if (apiToken.isNotBlank()) {
        header("Authorization", "Bearer $apiToken")
    }
    setBody(
        ChatCompletionRequest(
            model = model,
            messages = messages
        )
    )
}

/** Structured matching avoids retrying unrelated 400s such as malformed prompts or bad tokens. */
internal fun isModelUnavailableError(body: String): Boolean = runCatching {
    newsClientJson.parseToJsonElement(body)
        .jsonObject["error"]
        ?.jsonObject
        ?.get("code")
        ?.jsonPrimitive
        ?.contentOrNull == "model_unavailable"
}.getOrDefault(false)
