package network

import createNewsHttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import logging.AppLogger
import model.ChatCompletionRequest
import model.ChatCompletionResponse
import model.ChatMessage
import model.NewsItem
import model.Ticker24hr

/**
 * Generates a short, human-readable overview ("AI Insights") for a coin by
 * combining its 24-hour ticker with recent public news headlines about the
 * coin, using a free, OpenAI-compatible text model.
 *
 * Backend: **llm7.io** serving `gpt-oss:20b` (OpenAI's open-weights model). It works anonymously,
 * so AI Insights needs no setup. A user can optionally paste a free token from
 * https://dash.llm7.io in Settings to raise the limits (anonymous: 10 requests/min, 60/hour,
 * 500K tokens/day; free token: 40/min, 100/hour, 1M tokens/day — see https://docs.llm7.io/limits).
 * The request/response contract is the standard OpenAI chat-completions shape, so swapping the
 * provider (any other OpenAI-compatible endpoint) only requires editing [AiConfig].
 *
 * Previously this used Pollinations, which *required* a user-supplied key and still answered
 * HTTP 402 to essentially every real request from this app — a valid key made no difference —
 * so the insight card permanently told users to add a key they already had.
 *
 * Note that `gpt-oss:20b` is a *reasoning* model: it spends completion tokens thinking before it
 * answers. Capping `max_tokens` makes it burn the whole budget on reasoning and return an empty
 * `content`, so the request deliberately sends no cap — see [model.ChatCompletionRequest].
 *
 * Privacy: only public data (price/24h stats and public news headlines) is ever sent — never
 * wallet addresses, holdings or any personal data. As with any free hosted endpoint, the operator
 * can still log requests.
 */
class AiInsightService {
    private val httpClient = createNewsHttpClient()

    object AiConfig {
        /**
         * OpenAI-compatible API root. Swapping to another gateway is a one-line change here, since
         * [CHAT_COMPLETIONS_PATH] is fixed by the OpenAI contract rather than by the provider.
         */
        const val BASE_URL = "https://api.llm7.io/v1"

        /** Standard OpenAI chat-completions path, appended to [BASE_URL]. */
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"

        val ENDPOINT: String get() = "$BASE_URL$CHAT_COMPLETIONS_PATH"

        /** Open-weights model id served by the gateway. */
        const val MODEL = "gpt-oss:20b"
    }

    sealed interface InsightResult {
        data class Success(val text: String) : InsightResult

        /**
         * The gateway is throttling or refusing us for capacity reasons (402/429). Transient and
         * worth a Retry — distinct from [Failure] so the card can say so rather than showing a
         * generic error.
         */
        data object RateLimited : InsightResult

        data class Failure(val message: String) : InsightResult
    }

    suspend fun generateInsight(
        symbol: String,
        baseAsset: String,
        ticker: Ticker24hr,
        news: List<NewsItem>,
        apiToken: String
    ): InsightResult {
        return try {
            val response: HttpResponse = httpClient.post(AiConfig.ENDPOINT) {
                contentType(ContentType.Application.Json)
                // Optional: raises the rate limits. Omitted entirely when blank, which llm7.io
                // serves anonymously rather than rejecting.
                if (apiToken.isNotBlank()) {
                    header("Authorization", "Bearer $apiToken")
                }
                setBody(
                    ChatCompletionRequest(
                        model = AiConfig.MODEL,
                        messages = listOf(
                            ChatMessage(role = "system", content = SYSTEM_PROMPT),
                            ChatMessage(role = "user", content = buildUserPrompt(symbol, baseAsset, ticker, news))
                        )
                    )
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val text = response.body<ChatCompletionResponse>()
                        .choices.firstOrNull()?.message?.content?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        InsightResult.Success(text)
                    } else {
                        AppLogger.logger.w { "AiInsightService: empty completion for $symbol" }
                        InsightResult.Failure("Empty response")
                    }
                }

                // Capacity/quota pushback rather than a real failure — the card offers a Retry.
                HttpStatusCode.PaymentRequired,
                HttpStatusCode.TooManyRequests -> {
                    AppLogger.logger.w { "AiInsightService: rate limited (${response.status}) for $symbol" }
                    InsightResult.RateLimited
                }

                else -> {
                    AppLogger.logger.w {
                        "AiInsightService: ${response.status} for $symbol: ${response.bodyAsText().take(200)}"
                    }
                    InsightResult.Failure("HTTP ${response.status.value}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            AppLogger.logger.w(throwable = e) { "AiInsightService: timeout for $symbol" }
            InsightResult.Failure("Request timed out")
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "AiInsightService: error generating insight for $symbol" }
            InsightResult.Failure(e.message ?: "Unknown error")
        }
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        /** Cap the number of headlines sent to the model to keep the prompt small. */
        const val MAX_NEWS_ITEMS = 10

        /** Trim each headline's description to bound token usage. */
        const val MAX_SNIPPET_CHARS = 160

        /**
         * Builds the user message: 24h market data plus, when available, a compact list of
         * the coin's most recent news headlines. Pure (no I/O) so it is directly unit-tested.
         */
        internal fun buildUserPrompt(
            symbol: String,
            baseAsset: String,
            ticker: Ticker24hr,
            news: List<NewsItem>
        ): String {
            val changePct = ticker.priceChangePercent.toDoubleOrNull() ?: 0.0
            val direction = when {
                changePct > 0 -> "up"
                changePct < 0 -> "down"
                else -> "flat"
            }
            val recentNews = news.take(MAX_NEWS_ITEMS)
            return buildString {
                if (recentNews.isEmpty()) {
                    appendLine("Give a brief overview for the $baseAsset pair $symbol using ONLY the 24h market data below (no recent news is available).")
                } else {
                    appendLine("Give a brief overview for the $baseAsset pair $symbol by combining the 24h market data and the recent news headlines below.")
                }
                appendLine()
                appendLine("24h MARKET DATA")
                appendLine("Last price: ${ticker.lastPrice}")
                appendLine("24h change: ${ticker.priceChangePercent}% ($direction)")
                appendLine("24h high: ${ticker.highPrice}")
                appendLine("24h low: ${ticker.lowPrice}")
                appendLine("24h open: ${ticker.openPrice}")
                appendLine("24h base volume: ${ticker.volume} $baseAsset")
                appendLine("24h quote volume: ${ticker.quoteVolume}")
                appendLine("Weighted average price: ${ticker.weightedAvgPrice}")

                if (recentNews.isNotEmpty()) {
                    appendLine()
                    appendLine("RECENT NEWS (newest first)")
                    recentNews.forEach { item ->
                        val headline = item.title.collapseWhitespace()
                        val snippet = item.description.collapseWhitespace().take(MAX_SNIPPET_CHARS)
                        append("- [${item.source}] $headline")
                        if (snippet.isNotBlank()) {
                            append(" — $snippet")
                        }
                        appendLine()
                    }
                }
            }
        }

        /**
         * Identity of the headlines that would reach the model — the part of the input that
         * actually changes what the overview says. Takes the same [MAX_NEWS_ITEMS] slice as
         * [buildUserPrompt] so the cache key and the prompt can't drift apart.
         *
         * Sorted, so this describes the *set* of headlines rather than their order: an overview
         * built from the same stories is worth reusing however they were arranged. That also keeps
         * the key stable against any residual ordering jitter between two loads.
         *
         * Falls back to the title where a feed gives no link, so two different headlines never
         * collapse into the same fingerprint.
         */
        internal fun newsFingerprint(news: List<NewsItem>): String =
            news.take(MAX_NEWS_ITEMS)
                .map { it.link.ifBlank { it.title } }
                .sorted()
                .joinToString("\n")

        private fun String.collapseWhitespace(): String =
            replace(Regex("\\s+"), " ").trim()

        const val SYSTEM_PROMPT =
            "You are a concise crypto market assistant embedded in a price-tracker app. " +
                "You are given 24-hour ticker data for a single trading pair and, when available, a list of recent news headlines about the coin. " +
                "Write a neutral, factual overview in 4 to 5 short sentences that combines both. " +
                "Cover momentum and volatility (where the last price sits between the 24h high and low) and what the volume suggests about activity, " +
                "then summarise the dominant themes or sentiment in the news and any notable catalysts. " +
                "If no news is provided, base the overview on the market data alone and do not invent news. " +
                "Do NOT give buy, sell or hold recommendations, price predictions, or financial advice. " +
                "Use plain sentences only, no markdown headings or bullet lists, and keep it under 120 words."
    }
}
