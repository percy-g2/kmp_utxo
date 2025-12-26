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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logging.AppLogger
import model.UiKline
import kotlin.time.ExperimentalTime

/**
 * WebSocket service for Binance kline (candlestick) streams
 * Uses Binance WebSocket Streams: <symbol>@kline_<interval>
 * Provides real-time kline updates for charting
 */
@OptIn(ExperimentalTime::class)
class KlineWebSocketService {
    companion object {
        private const val RECONNECTION_DELAY_MS = 2000L
        private const val MAX_RECONNECTION_RETRIES = 3
    }
    
    private val webSocketClient = getWebSocketClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private var webSocketJob: Job? = null
    private var isConnected = false
    private var reconnectRetries = 0
    
    val klineData: StateFlow<UiKline?>
        field = MutableStateFlow(null)
    
    val error: StateFlow<String?>
        field = MutableStateFlow(null)
    
    private var currentSymbol: String? = null
    private var currentInterval: String? = null
    
    /**
     * Connect to kline stream for a symbol and interval
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     * @param interval Kline interval (e.g., "1m", "5m", "15m", "1h", "4h", "1d")
     */
    fun connect(symbol: String, interval: String) {
        val connectionKey = "$symbol:$interval"
        val currentKey = currentSymbol?.let { "$it:${currentInterval ?: ""}" }
        
        if (currentKey == connectionKey && isConnected) {
            AppLogger.logger.d { "KlineWebSocket: Already connected to $connectionKey" }
            return
        }
        
        disconnect()
        currentSymbol = symbol
        currentInterval = interval
        reconnectRetries = 0
        error.value = null
        
        webSocketJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            supervisorScope {
                while (isActive) {
                    try {
                        isConnected = true
                        reconnectRetries = 0
                        AppLogger.logger.d { "KlineWebSocket: Connecting to kline stream for $symbol@$interval" }
                        
                        // Binance WebSocket Stream format: <symbol>@kline_<interval>
                        val streamName = "${symbol.lowercase()}@kline_${interval.lowercase()}"
                        
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
                                            processKlineUpdate(message)
                                        } catch (e: Exception) {
                                            AppLogger.logger.e(throwable = e) { 
                                                "KlineWebSocket: Error processing kline update" 
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
                        reconnectRetries = 0
                        AppLogger.logger.d(throwable = e) { 
                            "KlineWebSocket: Connection cancelled for $symbol@$interval" 
                        }
                        break
                    } catch (e: Exception) {
                        isConnected = false
                        if (isActive) {
                            reconnectRetries++
                            val errorMessage = "Failed to connect to kline stream: ${e.message ?: "Unknown error"}"
                            AppLogger.logger.e(throwable = e) { 
                                "KlineWebSocket: Error connecting to $symbol@$interval (retry $reconnectRetries/$MAX_RECONNECTION_RETRIES)" 
                            }
                            
                            if (reconnectRetries <= MAX_RECONNECTION_RETRIES) {
                                error.value = errorMessage
                                delay(RECONNECTION_DELAY_MS)
                            } else {
                                error.value = "Max reconnection retries reached"
                                AppLogger.logger.e { 
                                    "KlineWebSocket: Max reconnection retries reached for $symbol@$interval" 
                                }
                                break
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Process kline update from WebSocket stream
     * Binance WebSocket kline format:
     * {
     *   "e": "kline",
     *   "E": 123456789,
     *   "s": "BTCUSDT",
     *   "k": {
     *     "t": 123400000,  // openTime (0)
     *     "T": 123460000,  // closeTime (6)
     *     "s": "BTCUSDT",
     *     "i": "1m",
     *     "f": 100,
     *     "L": 200,
     *     "o": "0.0010",  // open (1)
     *     "h": "0.0020",  // high (2)
     *     "l": "0.0010",  // low (3)
     *     "c": "0.0020",  // close (4)
     *     "v": "10000",   // volume (5)
     *     "n": 100,
     *     "x": false,
     *     "q": "18",
     *     "V": "10000",
     *     "Q": "18"
     *   }
     * }
     */
    private fun processKlineUpdate(message: String) {
        try {
            val jsonObject = json.parseToJsonElement(message).jsonObject
            
            // Check if this is a kline event
            val eventType = jsonObject["e"]?.jsonPrimitive?.content
            if (eventType != "kline") {
                return
            }
            
            // Extract kline data from "k" field
            val klineObject = jsonObject["k"]?.jsonObject ?: return
            
            // Parse only the fields needed for OHLC
            val openTime = klineObject["t"]?.jsonPrimitive?.content?.toLongOrNull()
            val openPrice = klineObject["o"]?.jsonPrimitive?.content
            val highPrice = klineObject["h"]?.jsonPrimitive?.content
            val lowPrice = klineObject["l"]?.jsonPrimitive?.content
            val closePrice = klineObject["c"]?.jsonPrimitive?.content ?: return
            val closeTime = klineObject["T"]?.jsonPrimitive?.content?.toLongOrNull()
            val volume = klineObject["v"]?.jsonPrimitive?.content
            
            // Check if kline is closed (x: true means closed, x: false means ongoing)
            val isClosed = klineObject["x"]?.jsonPrimitive?.content?.toBoolean() == true
            
            // Process both closed and open klines
            // Closed klines add a new candle, open klines update the current candle
            if (closePrice.isNotEmpty()) {
                val kline = UiKline(
                    openTime = openTime,
                    openPrice = openPrice,
                    highPrice = highPrice,
                    lowPrice = lowPrice,
                    closePrice = closePrice,
                    volume = volume,
                    closeTime = closeTime
                )
                
                klineData.value = kline
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { 
                "KlineWebSocket: Failed to parse kline update: ${message.take(200)}" 
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
        reconnectRetries = 0
        currentSymbol = null
        currentInterval = null
        klineData.value = null
        error.value = null
        AppLogger.logger.d { "KlineWebSocket: Disconnected" }
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

