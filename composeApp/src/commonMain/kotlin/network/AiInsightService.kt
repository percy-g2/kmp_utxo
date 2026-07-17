package network

import createNewsHttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
 * Backend: **Pollinations AI** (https://pollinations.ai) — an open-source (MIT),
 * self-hostable generative-AI gateway listed under zebbern/no-cost-ai. The
 * request/response contract is the standard OpenAI chat-completions shape, so
 * swapping the provider (a self-hosted Pollinations instance, llm7.io, or any
 * other OpenAI-compatible endpoint) only requires editing [AiConfig].
 *
 * Privacy: only public data (price/24h stats and public news headlines) is ever
 * sent — never wallet addresses, holdings or any personal data. Requests are tagged
 * `private=true` so prompts stay out of Pollinations' public feed. Note that,
 * as with any free hosted endpoint, the operator can still log requests; this
 * is why the API key is user-supplied (Settings) rather than embedded.
 */
class AiInsightService {
    private val httpClient = createNewsHttpClient()

    object AiConfig {
        /** OpenAI-compatible chat-completions endpoint. */
        const val ENDPOINT = "https://text.pollinations.ai/openai"

        /** Model id understood by the gateway ("openai" maps to a GPT-class model on Pollinations). */
        const val MODEL = "openai"
    }

    sealed interface InsightResult {
        data class Success(val text: String) : InsightResult

        /** The endpoint requires a (free) API key or the account's budget is exhausted. */
        data object AuthRequired : InsightResult

        data class Failure(val message: String) : InsightResult
    }

    suspend fun generateInsight(
        symbol: String,
        baseAsset: String,
        ticker: Ticker24hr,
        news: List<NewsItem>,
        apiKey: String
    ): InsightResult {
        return try {
            val response: HttpResponse = httpClient.post(AiConfig.ENDPOINT) {
                // Keep prompts out of Pollinations' public feed (ignored by other gateways).
                parameter("private", "true")
                contentType(ContentType.Application.Json)
                if (apiKey.isNotBlank()) {
                    header("Authorization", "Bearer $apiKey")
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

                HttpStatusCode.Unauthorized,
                HttpStatusCode.PaymentRequired,
                HttpStatusCode.Forbidden -> {
                    AppLogger.logger.w { "AiInsightService: auth/budget required (${response.status}) for $symbol" }
                    InsightResult.AuthRequired
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
