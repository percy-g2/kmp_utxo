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
import model.HlPerpState

/**
 * Real-time Hyperliquid PERP stream for a public wallet address.
 *
 * Connects once to wss://api.hyperliquid.xyz/ws and subscribes to the `clearinghouseState` channel,
 * whose frames carry `data.clearinghouseState` — the MAIN perp dex clearinghouse state (margin
 * summary + positions). It pushes a fresh frame every few seconds with an updated accountValue, so
 * perp equity and positions update live. (Positions on HIP-3 builder-deployed dexes are NOT included
 * here — those are HTTP-polled in WalletStream.)
 *
 * NOTE: the legacy `webData2` channel (which also bundled spot balances) is no longer accepted by
 * the API — it returns a parse error and never delivers, which is why the portfolio appeared static.
 * Spot balances are therefore sourced over HTTP (they change only on trades/transfers; their USD
 * value ticks live via the separate allMids feed).
 *
 * The HTTP snapshot in [HyperliquidService] bootstraps before the first live frame; once a frame
 * arrives the WS owns perp state (see WalletStream in PortfolioViewModel).
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

                            // Live main-dex perp clearinghouse state. Frames arrive every few
                            // seconds with an updated accountValue/positions (the legacy webData2
                            // channel is rejected by the API and never delivers).
                            send(Frame.Text(subscribeFrame("clearinghouseState", user)))

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
                "clearinghouseState" -> {
                    // Frame shape: { channel, data: { dex, user, clearinghouseState: {...} } }.
                    // The main-dex perp state is nested under data.clearinghouseState.
                    val data = root["data"]?.jsonObject ?: return
                    data["clearinghouseState"]?.let {
                        perpState.value = json.decodeFromJsonElement(HlPerpState.serializer(), it)
                    }
                }
                else -> {} // subscriptionResponse, error, etc. — ignore
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "PortfolioWebSocket: parse failed: ${text.take(200)}" }
        }
    }

    fun disconnect() {
        webSocketJob?.cancel()
        webSocketJob = null
        isConnected = false
        currentUser = null
        perpState.value = null
        connectionError.value = null
        AppLogger.logger.d { "PortfolioWebSocket: disconnected" }
    }

    fun close() {
        disconnect()
    }
}
