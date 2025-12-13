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
    companion object {
        private const val RECONNECTION_DELAY_MS = 3000L
        private const val RECONNECTION_DELAY_SHORT_MS = 1000L
    }
    
    private val webSocketClient = getWebSocketClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private var webSocketJob: Job? = null
    private var isConnected = false
    
    private val _orderBookData = MutableStateFlow<OrderBookData?>(null)
    val orderBookData: StateFlow<OrderBookData?> = _orderBookData.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private var currentSymbol: String? = null
    private var lastUpdateId: Long = 0
    
    // Maintain current order book state for merging incremental updates
    private var currentBids: MutableMap<String, OrderBookLevel> = mutableMapOf()
    private var currentAsks: MutableMap<String, OrderBookLevel> = mutableMapOf()
    
    // Flag to trigger reconnection when update ID validation fails
    private var needsReconnect = false
    
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
        needsReconnect = false
        _error.value = null // Clear any previous errors
        
        webSocketJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            supervisorScope {
                while (isActive) {
                    try {
                        isConnected = true
                        needsReconnect = false
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
                                if (!isActive || needsReconnect) {
                                    isConnected = false
                                    break
                                }
                                
                                when (frame) {
                                    is Frame.Text -> {
                                        try {
                                            val message = frame.readText()
                                            processDepthUpdate(symbol, message)
                                            
                                            // Check if reconnection is needed after processing
                                            if (needsReconnect) {
                                                isConnected = false
                                                break
                                            }
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
                        
                        // If reconnection is needed, break and reconnect
                        if (needsReconnect && isActive) {
                            AppLogger.logger.d { 
                                "OrderBookWebSocket: Reconnecting due to update ID validation failure" 
                            }
                            delay(RECONNECTION_DELAY_SHORT_MS)
                            continue // Continue loop to reconnect
                        }
                    } catch (e: CancellationException) {
                        isConnected = false
                        needsReconnect = false
                        AppLogger.logger.d(throwable = e) { 
                            "OrderBookWebSocket: Connection cancelled for $symbol" 
                        }
                        break
                    } catch (e: Exception) {
                        isConnected = false
                        needsReconnect = false
                        if (isActive) {
                            val errorMessage = "Failed to connect to order book: ${e.message ?: "Unknown error"}"
                            AppLogger.logger.e(throwable = e) { 
                                "OrderBookWebSocket: Error connecting to $symbol" 
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
     * Process depth update from WebSocket stream
     * Handles both snapshot (first message) and incremental updates
     * 
     * Binance WebSocket Protocol:
     * - First message is a snapshot with full order book
     * - Subsequent messages are incremental updates containing only changed levels
     * - Update IDs must be validated: firstUpdateId should be lastUpdateId + 1
     * - Levels with quantity "0" should be removed from the order book
     */
    private fun processDepthUpdate(symbol: String, message: String) {
        try {
            // Try parsing as snapshot first (has lastUpdateId, no event fields)
            val snapshot = json.decodeFromString<OrderBookDepthSnapshot>(message)
            
            // Snapshot: Replace entire order book
            currentBids.clear()
            currentAsks.clear()
            
            snapshot.bids.forEach { level ->
                val price = level[0]
                val quantity = level[1]
                if (quantity != "0") {
                    currentBids[price] = OrderBookLevel(price = price, quantity = quantity)
                }
            }
            
            snapshot.asks.forEach { level ->
                val price = level[0]
                val quantity = level[1]
                if (quantity != "0") {
                    currentAsks[price] = OrderBookLevel(price = price, quantity = quantity)
                }
            }
            
            lastUpdateId = snapshot.lastUpdateId
            
            // Convert to sorted lists (bids descending, asks ascending)
            val sortedBids = currentBids.values.sortedByDescending { it.priceDouble }
            val sortedAsks = currentAsks.values.sortedBy { it.priceDouble }
            
            _orderBookData.value = OrderBookData(
                symbol = symbol,
                bids = sortedBids,
                asks = sortedAsks,
                lastUpdateId = lastUpdateId,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
            
            AppLogger.logger.d { 
                "OrderBookWebSocket: Snapshot processed for $symbol, lastUpdateId=$lastUpdateId, bids=${sortedBids.size}, asks=${sortedAsks.size}" 
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            // If snapshot parsing fails, try parsing as incremental update
            try {
                val depthUpdate = json.decodeFromString<OrderBookDepthUpdate>(message)
                
                // Validate update ID according to Binance protocol
                // firstUpdateId should be lastUpdateId + 1
                // If not, we've missed updates and should reconnect
                if (lastUpdateId > 0) {
                    val expectedFirstUpdateId = lastUpdateId + 1
                    if (depthUpdate.firstUpdateId != expectedFirstUpdateId) {
                        if (depthUpdate.firstUpdateId < expectedFirstUpdateId) {
                            AppLogger.logger.w { 
                                "OrderBookWebSocket: Out-of-order update detected. Expected firstUpdateId=$expectedFirstUpdateId, got ${depthUpdate.firstUpdateId}. Reconnecting..." 
                            }
                        } else {
                            AppLogger.logger.w { 
                                "OrderBookWebSocket: Missed updates detected. Expected firstUpdateId=$expectedFirstUpdateId, got ${depthUpdate.firstUpdateId}. Reconnecting..." 
                            }
                        }
                        // Set flag to trigger reconnection
                        needsReconnect = true
                        return
                    }
                }
                
                // Merge incremental update with current order book state
                // Update bids
                depthUpdate.bids.forEach { level ->
                    val price = level[0]
                    val quantity = level[1]
                    
                    if (quantity == "0" || quantity.toDoubleOrNull() == 0.0) {
                        // Remove level if quantity is zero
                        currentBids.remove(price)
                    } else {
                        // Update or add level
                        currentBids[price] = OrderBookLevel(price = price, quantity = quantity)
                    }
                }
                
                // Update asks
                depthUpdate.asks.forEach { level ->
                    val price = level[0]
                    val quantity = level[1]
                    
                    if (quantity == "0" || quantity.toDoubleOrNull() == 0.0) {
                        // Remove level if quantity is zero
                        currentAsks.remove(price)
                    } else {
                        // Update or add level
                        currentAsks[price] = OrderBookLevel(price = price, quantity = quantity)
                    }
                }
                
                lastUpdateId = depthUpdate.finalUpdateId
                
                // Convert to sorted lists (bids descending, asks ascending)
                val sortedBids = currentBids.values.sortedByDescending { it.priceDouble }
                val sortedAsks = currentAsks.values.sortedBy { it.priceDouble }
                
                _orderBookData.value = OrderBookData(
                    symbol = symbol,
                    bids = sortedBids,
                    asks = sortedAsks,
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
        needsReconnect = false
        currentSymbol = null
        lastUpdateId = 0
        currentBids.clear()
        currentAsks.clear()
        _orderBookData.value = null
        _error.value = null
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

