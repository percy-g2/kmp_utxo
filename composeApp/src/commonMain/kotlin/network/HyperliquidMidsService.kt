package network

import getWebSocketClient
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logging.AppLogger

/**
 * Real-time Hyperliquid mid-price stream, shared across ALL tracked wallets.
 *
 * Subscribes once to the GLOBAL, user-independent `allMids` channel on
 * wss://api.hyperliquid.xyz/ws. Each frame is `{ channel:"allMids", data:{ mids:{ "@1":"13.9",
 * "PURR/USDC":"0.079", "BTC":"..." } } }` — a market-id -> mid-price map covering perps and spot.
 * The portfolio uses it to value non-stablecoin SPOT holdings live and to tick perp position
 * marks/PnL between account frames (perp equity/positions arrive via each wallet's
 * [PortfolioWebSocketService] `clearinghouseState` stream).
 *
 * One instance serves the whole screen — allMids is wallet-independent, so per-wallet sockets would
 * be pure duplication. Mirrors the reconnect/heartbeat pattern of [PortfolioWebSocketService] (a
 * supervised while(isActive) loop with fixed backoff + a 30s application-level ping to defeat the
 * 60s idle close), with two deliberate differences:
 *   - [connect] takes no user argument.
 *   - [disconnect] RETAINS the last-known [mids]; only [close] clears them. A transient reconnect or
 *     a screen pause must NOT blank every spot usdValue (which would drop the total and flush the
 *     allocation chart, causing a visible flicker).
 */
class HyperliquidMidsService {
    companion object {
        private const val WS_HOST = "api.hyperliquid.xyz"
        private const val WS_PATH = "/ws"
        private const val RECONNECTION_DELAY_MS = 3000L
        private const val PING_INTERVAL_MS = 30_000L
        private const val PING_MESSAGE = """{"method":"ping"}"""
        private const val SUBSCRIBE_MESSAGE = """{"method":"subscribe","subscription":{"type":"allMids"}}"""
    }

    private val webSocketClient = getWebSocketClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Live market-id -> mid-price (USD). Retained across reconnects; cleared only by [close]. */
    val mids: StateFlow<Map<String, Double>>
        field = MutableStateFlow(emptyMap())

    /** Non-null while the connection is down (diagnostic only — does NOT drive the wallet stale banner). */
    val connectionError: StateFlow<String?>
        field = MutableStateFlow(null)

    private var webSocketJob: Job? = null
    private var isConnected = false

    fun connect() {
        if (isConnected) {
            AppLogger.logger.d { "HyperliquidMids: already connected" }
            return
        }
        // Cancel any prior job without wiping mids (disconnect keeps them).
        webSocketJob?.cancel()

        webSocketJob = CoroutineScope(Dispatchers.Default).launch {
            supervisorScope {
                while (isActive) {
                    try {
                        AppLogger.logger.d { "HyperliquidMids: connecting" }
                        webSocketClient.wss(
                            method = HttpMethod.Get,
                            host = WS_HOST,
                            path = WS_PATH,
                            request = {
                                header(HttpHeaders.ContentType, ContentType.Application.Json)
                            }
                        ) {
                            isConnected = true
                            connectionError.value = null

                            send(Frame.Text(SUBSCRIBE_MESSAGE))

                            // Application-level heartbeat to defeat the 60s idle close.
                            val pingJob = launch {
                                while (isActive) {
                                    delay(PING_INTERVAL_MS)
                                    try {
                                        send(Frame.Text(PING_MESSAGE))
                                    } catch (e: Exception) {
                                        break
                                    }
                                }
                            }

                            try {
                                for (frame in incoming) {
                                    if (!isActive) break
                                    when (frame) {
                                        is Frame.Text -> process(frame.readText())
                                        is Frame.Ping -> send(Frame.Pong(frame.data))
                                        is Frame.Close -> throw CancellationException("WebSocket closed")
                                        else -> {}
                                    }
                                }
                            } finally {
                                pingJob.cancel()
                            }
                        }
                        isConnected = false
                    } catch (e: CancellationException) {
                        isConnected = false
                        break
                    } catch (e: Exception) {
                        isConnected = false
                        if (isActive) {
                            connectionError.value = e.message ?: "Connection lost"
                            AppLogger.logger.e(throwable = e) { "HyperliquidMids: error" }
                            delay(RECONNECTION_DELAY_MS)
                        }
                    }
                }
            }
        }
    }

    private fun process(text: String) {
        try {
            val root = json.parseToJsonElement(text).jsonObject
            when (root["channel"]?.jsonPrimitive?.content) {
                "pong" -> return // heartbeat ack
                "allMids" -> {
                    val midsObj = root["data"]?.jsonObject?.get("mids")?.jsonObject ?: return
                    val parsed = HashMap<String, Double>(midsObj.size)
                    midsObj.forEach { (market, value) ->
                        val px = value.jsonPrimitive.content.toDoubleOrNull()
                        if (px != null && px.isFinite() && px > 0.0) parsed[market] = px
                    }
                    if (parsed.isNotEmpty()) {
                        // Merge (not replace): a partial frame must never drop known markets.
                        mids.value = mids.value + parsed
                    }
                }
                else -> {} // subscriptionResponse, error, etc. — ignore
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "HyperliquidMids: parse failed: ${text.take(200)}" }
        }
    }

    /** Drop the socket but KEEP the last-known [mids] so spot valuation stays stable across a pause/blip. */
    fun disconnect() {
        webSocketJob?.cancel()
        webSocketJob = null
        isConnected = false
        connectionError.value = null
        AppLogger.logger.d { "HyperliquidMids: disconnected (mids retained)" }
    }

    /** Full teardown: disconnect and clear the retained prices. */
    fun close() {
        disconnect()
        mids.value = emptyMap()
    }
}
