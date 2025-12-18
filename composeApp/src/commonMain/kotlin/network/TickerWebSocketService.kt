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
import model.Ticker
import model.Ticker24hr
import kotlin.time.ExperimentalTime

/**
 * WebSocket service for Binance ticker stream
 * Uses Binance WebSocket Streams: <symbol>@ticker
 * Provides real-time 24hr ticker statistics
 */
@OptIn(ExperimentalTime::class)
class TickerWebSocketService {
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
    
    val tickerData: StateFlow<Ticker24hr?>
        field = MutableStateFlow(null)
    
    private var currentSymbol: String? = null
    
    /**
     * Connect to ticker stream for a symbol
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     */
    fun connect(symbol: String) {
        if (currentSymbol == symbol && isConnected) {
            AppLogger.logger.d { "TickerWebSocket: Already connected to $symbol" }
            return
        }
        
        disconnect()
        currentSymbol = symbol
        
        webSocketJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            supervisorScope {
                while (isActive) {
                    try {
                        isConnected = true
                        AppLogger.logger.d { "TickerWebSocket: Connecting to ticker stream for $symbol" }
                        
                        // Binance WebSocket Stream format: <symbol>@ticker
                        val streamName = "${symbol.lowercase()}@ticker"
                        
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
                                            processTickerUpdate(symbol, message)
                                        } catch (e: Exception) {
                                            AppLogger.logger.e(throwable = e) { 
                                                "TickerWebSocket: Error processing ticker update" 
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
                            "TickerWebSocket: Connection cancelled for $symbol" 
                        }
                        break
                    } catch (e: Exception) {
                        isConnected = false
                        if (isActive) {
                            AppLogger.logger.e(throwable = e) { 
                                "TickerWebSocket: Error connecting to $symbol" 
                            }
                            delay(RECONNECTION_DELAY_MS)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Process ticker update from WebSocket stream
     * Converts WebSocket Ticker format to Ticker24hr format for UI compatibility
     */
    private fun processTickerUpdate(symbol: String, message: String) {
        try {
            val ticker = json.decodeFromString<Ticker>(message)
            
            // Convert WebSocket Ticker format to Ticker24hr format
            val ticker24hr = Ticker24hr(
                symbol = ticker.symbol,
                priceChange = ticker.priceChange,
                priceChangePercent = ticker.priceChangePercent,
                weightedAvgPrice = ticker.weightedAvgPrice,
                prevClosePrice = ticker.firstTradePrice, // First trade price before 24hr window
                lastPrice = ticker.lastPrice,
                lastQty = ticker.lastQuantity,
                bidPrice = ticker.bestBidPrice,
                bidQty = ticker.bestBidQuantity,
                askPrice = ticker.bestAskPrice,
                askQty = ticker.bestAskQuantity,
                openPrice = ticker.openPrice,
                highPrice = ticker.highPrice,
                lowPrice = ticker.lowPrice,
                volume = ticker.totalTradedBaseAssetVolume,
                quoteVolume = ticker.totalTradedQuoteAssetVolume,
                openTime = ticker.statisticsOpenTime,
                closeTime = ticker.statisticsCloseTime
            )
            
            tickerData.value = ticker24hr
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { 
                "TickerWebSocket: Failed to parse ticker update: ${message.take(200)}" 
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
        tickerData.value = null
        AppLogger.logger.d { "TickerWebSocket: Disconnected" }
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

