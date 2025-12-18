package trading.api

import getWebSocketClient
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import logging.AppLogger
import trading.data.AggTrade

/**
 * WebSocket service for Binance aggregated trades stream
 * 
 * WHY: Real-time trade flow data is critical for scalping.
 * WebSocket provides lower latency than polling REST API.
 * 
 * Stream format: <symbol>@aggTrade
 */
class AggTradeWebSocketService {
    companion object {
        private const val RECONNECTION_DELAY_MS = 3000L
    }
    
    private val webSocketClient = getWebSocketClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private var webSocketJob: Job? = null
    private var isConnected = false
    
    /**
     * Most recent aggregated trades (most recent first)
     * Maintains a rolling window of recent trades
     */
    private val recentTrades = MutableStateFlow<List<AggTrade>>(emptyList())
    val trades: StateFlow<List<AggTrade>> = recentTrades.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private var currentSymbol: String? = null
    private val maxTradesInMemory = 1000  // Keep last 1000 trades
    
    /**
     * Connect to aggregated trades stream for a symbol
     * 
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     */
    fun connect(symbol: String) {
        if (currentSymbol == symbol && isConnected) {
            AppLogger.logger.d { "AggTradeWebSocket: Already connected to $symbol" }
            return
        }
        
        disconnect()
        currentSymbol = symbol
        _error.value = null
        
        webSocketJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            supervisorScope {
                while (isActive) {
                    try {
                        isConnected = true
                        AppLogger.logger.d { "AggTradeWebSocket: Connecting to aggTrade stream for $symbol" }
                        
                        val streamName = "${symbol.lowercase()}@aggTrade"
                        
                        webSocketClient.wss(
                            method = HttpMethod.Get,
                            host = "stream.binance.com",
                            path = "/ws/$streamName",
                            request = {
                                header(HttpHeaders.ContentType, ContentType.Application.Json)
                            }
                        ) {
                            for (frame in incoming) {
                                if (!isActive) {
                                    isConnected = false
                                    break
                                }
                                
                                when (frame) {
                                    is Frame.Text -> {
                                        try {
                                            val message = frame.readText()
                                            processAggTrade(message)
                                        } catch (e: Exception) {
                                            AppLogger.logger.e(throwable = e) {
                                                "AggTradeWebSocket: Error processing trade"
                                            }
                                        }
                                    }
                                    is Frame.Ping -> send(Frame.Pong(frame.data))
                                    is Frame.Close -> {
                                        isConnected = false
                                        throw CancellationException("WebSocket closed")
                                    }
                                    else -> {}
                                }
                            }
                        }
                        isConnected = false
                    } catch (e: CancellationException) {
                        isConnected = false
                        AppLogger.logger.d(throwable = e) {
                            "AggTradeWebSocket: Connection cancelled for $symbol"
                        }
                        break
                    } catch (e: Exception) {
                        isConnected = false
                        if (isActive) {
                            val errorMessage = "Failed to connect to aggTrade stream: ${e.message ?: "Unknown error"}"
                            AppLogger.logger.e(throwable = e) {
                                "AggTradeWebSocket: Error connecting to $symbol"
                            }
                            _error.value = errorMessage
                            delay(RECONNECTION_DELAY_MS)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Process aggregated trade message from WebSocket
     */
    private fun processAggTrade(message: String) {
        try {
            val trade = json.decodeFromString<AggTrade>(message)
            
            // Add to recent trades (most recent first)
            val current = recentTrades.value.toMutableList()
            current.add(0, trade)  // Add at beginning
            
            // Keep only most recent trades
            if (current.size > maxTradesInMemory) {
                current.removeAt(current.size - 1)
            }
            
            recentTrades.value = current
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) {
                "AggTradeWebSocket: Failed to parse trade message: ${message.take(200)}"
            }
        }
    }
    
    /**
     * Disconnect from current stream
     */
    fun disconnect() {
        webSocketJob?.cancel()
        webSocketJob = null
        isConnected = false
        currentSymbol = null
        recentTrades.value = emptyList()
        _error.value = null
        AppLogger.logger.d { "AggTradeWebSocket: Disconnected" }
    }
    
    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * Cleanup resources
     */
    fun close() {
        disconnect()
    }
}

