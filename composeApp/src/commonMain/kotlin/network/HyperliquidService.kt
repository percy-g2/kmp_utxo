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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import logging.AppLogger
import model.HlPerpDex
import model.HlPerpState
import model.HlSpotAssetCtx
import model.HlSpotMeta
import model.HlSpotState
import model.buildSpotTokenPairMap

/** Result of [HyperliquidService.verifyAccount]: a real account, a reachable-but-empty one, or no reply. */
enum class HlAccountCheck { Active, Empty, Unreachable }

/**
 * Static spot metadata joined with an initial price snapshot, from one `spotMetaAndAssetCtxs` call.
 * [tokenIndexToPair] maps a spot token index to its USDC market name (the `allMids` key);
 * [seedPrices] maps that market name to its bootstrap mid price (live `allMids` supersedes it).
 */
data class SpotMetaInfo(
    val tokenIndexToPair: Map<Int, String>,
    val seedPrices: Map<String, Double>,
)

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

    // Builder-deployed (HIP-3) perp dex names, fetched once and cached — the set changes rarely.
    private val dexNamesMutex = Mutex()
    private var cachedDexNames: List<String>? = null

    // Spot token->market mapping + bootstrap prices, fetched once and cached — the set changes rarely.
    private val spotMetaMutex = Mutex()
    private var cachedSpotMeta: SpotMetaInfo? = null

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

    /** Perp clearinghouse state for [user] on the main dex, or a builder-deployed [dex] when given. */
    suspend fun fetchPerpState(user: String, dex: String? = null): HlPerpState? =
        infoPost("clearinghouseState", user, dex)?.let { body ->
            runCatching { json.decodeFromString<HlPerpState>(body) }
                .onFailure { AppLogger.logger.e(throwable = it) { "Hyperliquid: failed to parse clearinghouseState" } }
                .getOrNull()
        }

    /**
     * Builder-deployed (HIP-3) perp dex names, fetched once and cached. The `perpDexs` array starts
     * with `null` (the main dex) followed by per-dex objects; we keep the non-null names.
     */
    suspend fun perpDexNames(): List<String> {
        cachedDexNames?.let { return it }
        return dexNamesMutex.withLock {
            cachedDexNames?.let { return@withLock it }
            val body = post("""{"type":"perpDexs"}""", label = "perpDexs") ?: return@withLock emptyList()
            val names = runCatching {
                json.decodeFromString<List<HlPerpDex?>>(body)
                    .filterNotNull()
                    .map { it.name }
                    .filter { it.isNotEmpty() }
            }.getOrElse {
                AppLogger.logger.e(throwable = it) { "Hyperliquid: failed to parse perpDexs" }
                emptyList()
            }
            // Cache only on success so a transient failure is retried on the next call.
            if (names.isNotEmpty()) cachedDexNames = names
            names
        }
    }

    /**
     * Perp state on every builder-deployed (HIP-3) alt dex where [user] has equity or a position.
     * webData2 and the main `clearinghouseState` cover only the main dex, so alt dexes must be
     * fetched separately. Collateral is isolated per dex, so each returned state's accountValue is
     * additive with the main dex (no shared pool to double-count). Empty dex accounts are dropped.
     */
    suspend fun fetchAltPerpStates(user: String): List<HlPerpState> {
        val names = perpDexNames()
        if (names.isEmpty()) return emptyList()
        return coroutineScope {
            names.map { dex -> async { fetchPerpState(user, dex) } }.awaitAll()
        }.filterNotNull().filter {
            it.assetPositions.isNotEmpty() || (it.marginSummary.accountValue.toDoubleOrNull() ?: 0.0) > 0.0
        }
    }

    /**
     * Static spot metadata + a bootstrap price snapshot, fetched once and cached (the token/market
     * set changes rarely). One `spotMetaAndAssetCtxs` call returns a 2-tuple JSON array
     * `[ HlSpotMeta, [HlSpotAssetCtx...] ]`: the first element builds the `tokenIndex -> market`
     * map; the second seeds initial mid prices so spot holdings are valued immediately, before the
     * live `allMids` socket connects. Cached only on a non-empty parse so a transient failure is
     * retried on the next call.
     */
    suspend fun spotMeta(): SpotMetaInfo? {
        cachedSpotMeta?.let { return it }
        return spotMetaMutex.withLock {
            cachedSpotMeta?.let { return@withLock it }
            val body = post("""{"type":"spotMetaAndAssetCtxs"}""", label = "spotMetaAndAssetCtxs")
                ?: return@withLock null
            val info = runCatching {
                val arr = json.parseToJsonElement(body).jsonArray
                val meta = json.decodeFromJsonElement(HlSpotMeta.serializer(), arr[0])
                val ctxs = json.decodeFromJsonElement(ListSerializer(HlSpotAssetCtx.serializer()), arr[1])
                val pairs = buildSpotTokenPairMap(meta.universe)
                val seed = ctxs.mapNotNull { c ->
                    c.midPx?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }?.let { c.coin to it }
                }.toMap()
                SpotMetaInfo(pairs, seed)
            }.getOrElse {
                AppLogger.logger.e(throwable = it) { "Hyperliquid: failed to parse spotMetaAndAssetCtxs" }
                null
            }
            if (info != null && info.tokenIndexToPair.isNotEmpty()) cachedSpotMeta = info
            info
        }
    }

    suspend fun fetchSpotState(user: String): HlSpotState? =
        infoPost("spotClearinghouseState", user)?.let { body ->
            runCatching { json.decodeFromString<HlSpotState>(body) }
                .onFailure { AppLogger.logger.e(throwable = it) { "Hyperliquid: failed to parse spotClearinghouseState" } }
                .getOrNull()
        }

    /**
     * Check whether [user] is a real Hyperliquid account before tracking it. The info endpoint
     * returns an empty/default body for any syntactically valid address, so we require an actual
     * footprint — a perp position, a spot balance, or non-zero account value/withdrawable.
     * [HlAccountCheck.Unreachable] (both calls failed) is distinct from [HlAccountCheck.Empty]
     * (reachable, but no Hyperliquid activity) so the caller can offer a retry vs. reject.
     */
    suspend fun verifyAccount(user: String): HlAccountCheck {
        val perp = fetchPerpState(user)
        val spot = fetchSpotState(user)
        if (perp == null && spot == null) return HlAccountCheck.Unreachable
        val hasFootprint =
            perp?.assetPositions?.isNotEmpty() == true ||
                (perp?.marginSummary?.accountValue?.toDoubleOrNull() ?: 0.0) > 0.0 ||
                (perp?.withdrawable?.toDoubleOrNull() ?: 0.0) > 0.0 ||
                spot?.balances?.isNotEmpty() == true
        return if (hasFootprint) HlAccountCheck.Active else HlAccountCheck.Empty
    }

    /**
     * POST {"type":<type>,"user":<user>[,"dex":<dex>]} to the info endpoint and return the raw body,
     * or null on failure. The request body is built directly to avoid coupling to
     * ContentNegotiation request serialization.
     */
    private suspend fun infoPost(type: String, user: String, dex: String? = null): String? {
        val requestBody = if (dex.isNullOrEmpty()) {
            """{"type":"$type","user":"$user"}"""
        } else {
            """{"type":"$type","user":"$user","dex":"$dex"}"""
        }
        return post(requestBody, label = type)
    }

    /** POST a raw JSON body to the info endpoint with retry/backoff; null on failure. */
    private suspend fun post(requestBody: String, label: String): String? {
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
                        AppLogger.logger.w { "Hyperliquid info ($label) returned ${response.status}" }
                        return null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == MAX_RETRIES - 1) {
                    AppLogger.logger.e(throwable = e) { "Hyperliquid info ($label) request failed" }
                    return null
                }
                delay(500L * (attempt + 1))
            }
        }
        return null
    }
}
