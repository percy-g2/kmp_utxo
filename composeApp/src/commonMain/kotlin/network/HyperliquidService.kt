package network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import logging.AppLogger
import model.HlPerpState
import model.HlSpotState

/**
 * Read-only HTTP client for the Hyperliquid info endpoint.
 *
 * Used to fetch an immediate portfolio snapshot for a public wallet address while
 * the live [PortfolioWebSocketService] connection is being established. Only the
 * public 0x address is ever sent — there is no authentication.
 */
class HyperliquidService {
    companion object {
        private const val INFO_URL = "https://api.hyperliquid.xyz/info"
        private const val MAX_RETRIES = 3
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val rateLimiter = RateLimiter(maxRequests = 10, windowDurationMillis = 1000)

    private val client = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 30_000
        }
    }

    fun close() {
        client.close()
    }

    suspend fun fetchPerpState(user: String): HlPerpState? =
        infoPost("clearinghouseState", user)?.let { body ->
            runCatching { json.decodeFromString<HlPerpState>(body) }
                .onFailure { AppLogger.logger.e(throwable = it) { "Hyperliquid: failed to parse clearinghouseState" } }
                .getOrNull()
        }

    suspend fun fetchSpotState(user: String): HlSpotState? =
        infoPost("spotClearinghouseState", user)?.let { body ->
            runCatching { json.decodeFromString<HlSpotState>(body) }
                .onFailure { AppLogger.logger.e(throwable = it) { "Hyperliquid: failed to parse spotClearinghouseState" } }
                .getOrNull()
        }

    /**
     * POST {"type":<type>,"user":<user>} to the info endpoint and return the raw body,
     * or null on failure. The request body is built directly to avoid coupling to
     * ContentNegotiation request serialization.
     */
    private suspend fun infoPost(type: String, user: String): String? {
        val requestBody = """{"type":"$type","user":"$user"}"""
        for (attempt in 0 until MAX_RETRIES) {
            try {
                rateLimiter.acquire()
                val response: HttpResponse = client.post(INFO_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
                when {
                    response.status == HttpStatusCode.OK -> return response.bodyAsText()
                    response.status.value == 429 -> delay(1000L * (attempt + 1))      // rate limited
                    response.status.value in 500..599 -> delay(500L * (attempt + 1))  // server error, back off
                    else -> {
                        AppLogger.logger.w { "Hyperliquid info ($type) returned ${response.status}" }
                        return null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == MAX_RETRIES - 1) {
                    AppLogger.logger.e(throwable = e) { "Hyperliquid info ($type) request failed" }
                    return null
                }
                delay(500L * (attempt + 1))
            }
        }
        return null
    }
}
