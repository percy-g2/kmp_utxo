package network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logging.AppLogger
import model.isValidHyperliquidAddress

/**
 * Read-only ENS reverse resolution for EVM addresses (Hyperliquid wallets are standard EVM
 * addresses). Maps a public 0x address to its primary ENS (.eth) name, used only to label
 * wallets. No key/auth; only the public address is sent.
 *
 * Endpoint: GET https://api.ensideas.com/ens/resolve/{address} — a free, no-key, CORS-enabled
 * resolver that returns `{ "address", "name", "displayName", "avatar" }`. `name` is the
 * primary ENS name or null when the address has none (in which case `displayName` is just a
 * truncated address, which we ignore).
 */
class EnsService {
    companion object {
        private const val RESOLVE_URL = "https://api.ensideas.com/ens/resolve/"
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val rateLimiter = RateLimiter(maxRequests = 5, windowDurationMillis = 1000)

    private val client = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
            requestTimeoutMillis = 15_000
        }
    }

    @Serializable
    private data class EnsResponse(
        @SerialName("name") val name: String? = null,
    )

    /**
     * Resolve [address] to its primary ENS name.
     *
     * Returns [EnsResult.Resolved] (name possibly null = "no ENS") only when the HTTP call
     * completed (200); any non-200 or network/parse failure yields [EnsResult.Failed] so the
     * caller can avoid stamping a resolution timestamp and retry in a later session.
     * Rethrows [CancellationException].
     */
    suspend fun resolveName(address: String): EnsResult {
        if (!isValidHyperliquidAddress(address)) return EnsResult.Failed
        return try {
            rateLimiter.acquire()
            val response: HttpResponse = client.get(RESOLVE_URL + address)
            if (response.status == HttpStatusCode.OK) {
                val parsed = runCatching { json.decodeFromString<EnsResponse>(response.bodyAsText()) }.getOrNull()
                EnsResult.Resolved(parsed?.name?.trim()?.takeIf { it.isNotEmpty() })
            } else {
                EnsResult.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "ENS: resolve failed for $address" }
            EnsResult.Failed
        }
    }

    fun close() {
        client.close()
    }
}

/** Outcome of an ENS lookup; distinguishes a completed call (cacheable) from a failure (retry). */
sealed interface EnsResult {
    /** HTTP completed; [name] is the primary ENS name or null when the address has none. */
    data class Resolved(val name: String?) : EnsResult

    /** Network/HTTP/parse failure — do not cache; retry later. */
    data object Failed : EnsResult
}
