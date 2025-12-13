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
import model.OrderBookData
import model.OrderBookDepthSnapshot
import model.OrderBookDepthUpdate
import model.OrderBookLevel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * WebSocket service for Binance order book depth streams
 * Uses Binance WebSocket Streams: <symbol>@depth<levels> or <symbol>@depth
 */
@OptIn(ExperimentalTime::class)
class OrderBookWebSocketService {
    private val webSocketClient = getWebSocketClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private var webSocketJob: Job? = null
    private var isConnected = false
    
    private val _orderBookData = MutableStateFlow<OrderBookData?>(null)
    val orderBookData: StateFlow<OrderBookData?> = _orderBookData.asStateFlow()
    
    private var currentSymbol: String? = null
    private var lastUpdateId: Long = 0
    
    /**
     * Connect to order book depth stream for a symbol
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     * @param levels Number of depth levels (5, 10, 20). Default is 20
     */
    fun connect(symbol: String, levels: Int = 20) {
        if (currentSymbol == symbol && isConnected) {
            AppLogger.logger.d { "OrderBookWebSocket: Already connected to $symbol" }
            return
        }
        
        disconnect()
        currentSymbol = symbol
        
        webSocketJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            supervisorScope {
                while (isActive) {
                    try {
                        isConnected = true
                        AppLogger.logger.d { "OrderBookWebSocket: Connecting to depth stream for $symbol" }
                        
                        // Binance WebSocket Stream format: <symbol>@depth<levels>
                        val streamName = if (levels == 20) {
                            "${symbol.lowercase()}@depth20"
                        } else {
                            "${symbol.lowercase()}@depth${levels}"
                        }
                        
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
                                            processDepthUpdate(symbol, message)
                                        } catch (e: Exception) {
                                            AppLogger.logger.e(throwable = e) { 
                                                "OrderBookWebSocket: Error processing depth update" 
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
                            "OrderBookWebSocket: Connection cancelled for $symbol" 
                        }
                        break
                    } catch (e: Exception) {
                        isConnected = false
                        if (isActive) {
                            AppLogger.logger.e(throwable = e) { 
                                "OrderBookWebSocket: Error connecting to $symbol" 
                            }
                            delay(3000) // Wait before reconnecting
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Process depth update from WebSocket stream
     * Handles both snapshot (first message) and incremental updates
     */
    private fun processDepthUpdate(symbol: String, message: String) {
        try {
            // Try parsing as snapshot first (has lastUpdateId, no event fields)
            val snapshot = json.decodeFromString<OrderBookDepthSnapshot>(message)
            
            // Convert to OrderBookLevel lists
            val bids = snapshot.bids.map { 
                OrderBookLevel(price = it[0], quantity = it[1]) 
            }
            val asks = snapshot.asks.map { 
                OrderBookLevel(price = it[0], quantity = it[1]) 
            }
            
            lastUpdateId = snapshot.lastUpdateId
            
            _orderBookData.value = OrderBookData(
                symbol = symbol,
                bids = bids,
                asks = asks,
                lastUpdateId = lastUpdateId,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        } catch (e: kotlinx.serialization.SerializationException) {
            // If snapshot parsing fails, try parsing as incremental update
            try {
                val depthUpdate = json.decodeFromString<OrderBookDepthUpdate>(message)
                
                // Convert to OrderBookLevel lists
                val bids = depthUpdate.bids.map { 
                    OrderBookLevel(price = it[0], quantity = it[1]) 
                }
                val asks = depthUpdate.asks.map { 
                    OrderBookLevel(price = it[0], quantity = it[1]) 
                }
                
                lastUpdateId = depthUpdate.finalUpdateId
                
                _orderBookData.value = OrderBookData(
                    symbol = symbol,
                    bids = bids,
                    asks = asks,
                    lastUpdateId = lastUpdateId,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            } catch (e2: Exception) {
                AppLogger.logger.e(throwable = e2) { 
                    "OrderBookWebSocket: Failed to parse depth message (tried both formats): ${message.take(200)}" 
                }
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { 
                "OrderBookWebSocket: Failed to parse depth update: ${message.take(200)}" 
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
        _orderBookData.value = null
        AppLogger.logger.d { "OrderBookWebSocket: Disconnected" }
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

