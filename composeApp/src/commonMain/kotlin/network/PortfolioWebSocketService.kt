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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logging.AppLogger
import model.HlPerpState
import model.HlSpotState

/**
 * Real-time Hyperliquid portfolio stream for a public wallet address.
 *
 * Connects once to wss://api.hyperliquid.xyz/ws and subscribes to a single `webData2` channel —
 * one atomic snapshot carrying BOTH:
 *   - data.clearinghouseState -> perps clearinghouse state. NOTE: MAIN perp dex only; positions on
 *     HIP-3 builder-deployed dexes are not included here (nor in the HTTP clearinghouseState call).
 *   - data.spotState.balances  -> spot balances, already reconciled by Hyperliquid: USDC pledged as
 *     perp collateral is reported as ~0, so it is NOT double-counted on top of accountValue.
 *
 * webData2 is the source of truth for live state; the HTTP snapshot in [HyperliquidService] is only
 * a bootstrap / offline fallback (see WalletStream in PortfolioViewModel). Using one channel also
 * avoids two streams racing to overwrite each other.
 *
 * Hyperliquid closes idle connections after 60s, so we send an application-level
 * {"method":"ping"} text frame every [PING_INTERVAL_MS]. This is distinct from the
 * WebSocket protocol ping/pong (server-initiated protocol pings are still answered).
 *
 * Mirrors the reconnect pattern of [TickerWebSocketService]: a supervised
 * while(isActive) loop with a fixed backoff delay.
 */
class PortfolioWebSocketService {
    companion object {
        private const val WS_HOST = "api.hyperliquid.xyz"
        private const val WS_PATH = "/ws"
        private const val RECONNECTION_DELAY_MS = 3000L
        private const val PING_INTERVAL_MS = 30_000L
        private const val PING_MESSAGE = """{"method":"ping"}"""
    }

    private val webSocketClient = getWebSocketClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val perpState: StateFlow<HlPerpState?>
        field = MutableStateFlow(null)

    val spotState: StateFlow<HlSpotState?>
        field = MutableStateFlow(null)

    /** Non-null while the connection is down (drives a "reconnecting" / stale banner). */
    val connectionError: StateFlow<String?>
        field = MutableStateFlow(null)

    private var webSocketJob: Job? = null
    private var currentUser: String? = null
    private var isConnected = false

    fun connect(user: String) {
        if (currentUser == user && isConnected) {
            AppLogger.logger.d { "PortfolioWebSocket: already connected for $user" }
            return
        }

        disconnect()
        currentUser = user

        webSocketJob = CoroutineScope(Dispatchers.Default).launch {
            supervisorScope {
                while (isActive) {
                    try {
                        AppLogger.logger.d { "PortfolioWebSocket: connecting for $user" }
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

                            // webData2 is a single atomic snapshot carrying BOTH the perps
                            // clearinghouseState and spot balances (data.spotState.balances).
                            // Using one source avoids two channels racing to overwrite each
                            // other (which previously flickered the screen empty). webData3
                            // buries perp data in a multi-dex array and isn't worth parsing.
                            send(Frame.Text(subscribeFrame("webData2", user)))

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
                            AppLogger.logger.e(throwable = e) { "PortfolioWebSocket: error for $user" }
                            delay(RECONNECTION_DELAY_MS)
                        }
                    }
                }
            }
        }
    }

    private fun subscribeFrame(type: String, user: String): String =
        """{"method":"subscribe","subscription":{"type":"$type","user":"$user"}}"""

    private fun process(text: String) {
        try {
            val root = json.parseToJsonElement(text).jsonObject
            when (root["channel"]?.jsonPrimitive?.content) {
                "pong" -> return // heartbeat ack
                "webData2" -> {
                    val data = root["data"]?.jsonObject ?: return
                    // Perps clearinghouse state is nested under data.clearinghouseState; the
                    // full spot snapshot under data.spotState ({ balances: [...] }).
                    data["clearinghouseState"]?.let {
                        perpState.value = json.decodeFromJsonElement(HlPerpState.serializer(), it)
                    }
                    data["spotState"]?.let { decodeSpot(it) }
                }
                else -> {} // subscriptionResponse, error, etc. — ignore
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "PortfolioWebSocket: parse failed: ${text.take(200)}" }
        }
    }

    private fun decodeSpot(element: JsonElement) {
        runCatching {
            spotState.value = json.decodeFromJsonElement(HlSpotState.serializer(), element)
        }.onFailure {
            AppLogger.logger.e(throwable = it) { "PortfolioWebSocket: failed to decode spot state" }
        }
    }

    fun disconnect() {
        webSocketJob?.cancel()
        webSocketJob = null
        isConnected = false
        currentUser = null
        perpState.value = null
        spotState.value = null
        connectionError.value = null
        AppLogger.logger.d { "PortfolioWebSocket: disconnected" }
    }

    fun close() {
        disconnect()
    }
}
