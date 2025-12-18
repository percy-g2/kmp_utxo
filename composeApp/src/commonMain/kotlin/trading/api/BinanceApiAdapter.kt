package trading.api

import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logging.AppLogger
import model.OrderBookData
import model.OrderBookLevel
import trading.data.AggTrade

/**
 * Binance REST API adapter for trading engine
 * 
 * WHY: Centralized API access with proper error handling and rate limiting
 * 
 * Endpoints implemented:
 * - GET /api/v3/depth - Order book depth
 * - GET /api/v3/aggTrades - Aggregated trades
 * - GET /api/v3/ticker/bookTicker - Best bid/ask
 * - GET /api/v3/klines - Kline/candlestick data
 */
class BinanceApiAdapter(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.binance.com"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Fetches order book depth snapshot
     * 
     * GET /api/v3/depth
     * 
     * @param symbol Trading pair (e.g., "BTCUSDT")
     * @param limit Number of levels (5, 10, 20, 50, 100, 500, 1000, 5000)
     * @return Order book data
     */
    suspend fun getDepth(
        symbol: String,
        limit: Int = 20
    ): OrderBookData? {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/v3/depth") {
                parameter("symbol", symbol)
                parameter("limit", limit)
            }
            
            if (response.status == HttpStatusCode.OK) {
                val jsonText = response.bodyAsText()
                val jsonObject = json.parseToJsonElement(jsonText).jsonObject
                
                val bids = jsonObject["bids"]?.jsonArray?.mapNotNull { level ->
                    val arr = level.jsonArray
                    if (arr.size >= 2) {
                        OrderBookLevel(
                            price = arr[0].jsonPrimitive.content,
                            quantity = arr[1].jsonPrimitive.content
                        )
                    } else null
                } ?: emptyList()
                
                val asks = jsonObject["asks"]?.jsonArray?.mapNotNull { level ->
                    val arr = level.jsonArray
                    if (arr.size >= 2) {
                        OrderBookLevel(
                            price = arr[0].jsonPrimitive.content,
                            quantity = arr[1].jsonPrimitive.content
                        )
                    } else null
                } ?: emptyList()
                
                val lastUpdateId = jsonObject["lastUpdateId"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                
                OrderBookData(
                    symbol = symbol,
                    bids = bids.sortedByDescending { it.priceDouble },
                    asks = asks.sortedBy { it.priceDouble },
                    lastUpdateId = lastUpdateId,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                AppLogger.logger.w { "BinanceApiAdapter: Failed to fetch depth. Status: ${response.status}" }
                null
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "BinanceApiAdapter: Error fetching depth for $symbol" }
            null
        }
    }
    
    /**
     * Fetches aggregated trades
     * 
     * GET /api/v3/aggTrades
     * 
     * @param symbol Trading pair
     * @param limit Number of trades (default 500, max 1000)
     * @return List of aggregated trades (most recent first)
     */
    suspend fun getAggTrades(
        symbol: String,
        limit: Int = 500
    ): List<AggTrade> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/v3/aggTrades") {
                parameter("symbol", symbol)
                parameter("limit", limit.coerceIn(1, 1000))
            }
            
            if (response.status == HttpStatusCode.OK) {
                val jsonText = response.bodyAsText()
                json.decodeFromString<List<AggTrade>>(jsonText)
            } else {
                AppLogger.logger.w { "BinanceApiAdapter: Failed to fetch aggTrades. Status: ${response.status}" }
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "BinanceApiAdapter: Error fetching aggTrades for $symbol" }
            emptyList()
        }
    }
    
    /**
     * Fetches best bid/ask prices
     * 
     * GET /api/v3/ticker/bookTicker
     * 
     * @param symbol Trading pair
     * @return Pair of (bestBid, bestAsk) or null if error
     */
    suspend fun getBookTicker(symbol: String): Pair<Double, Double>? {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/v3/ticker/bookTicker") {
                parameter("symbol", symbol)
            }
            
            if (response.status == HttpStatusCode.OK) {
                val jsonText = response.bodyAsText()
                val jsonObject = json.parseToJsonElement(jsonText).jsonObject
                
                val bidPrice = jsonObject["bidPrice"]?.jsonPrimitive?.content?.toDoubleOrNull()
                val askPrice = jsonObject["askPrice"]?.jsonPrimitive?.content?.toDoubleOrNull()
                
                if (bidPrice != null && askPrice != null) {
                    Pair(bidPrice, askPrice)
                } else {
                    null
                }
            } else {
                AppLogger.logger.w { "BinanceApiAdapter: Failed to fetch bookTicker. Status: ${response.status}" }
                null
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "BinanceApiAdapter: Error fetching bookTicker for $symbol" }
            null
        }
    }
    
    /**
     * Fetches kline/candlestick data
     * 
     * GET /api/v3/klines
     * 
     * @param symbol Trading pair
     * @param interval Kline interval (1s, 1m, 5m, etc.)
     * @param limit Number of klines (default 100, max 1000)
     * @return List of kline data (as JSON objects - parse as needed)
     */
    suspend fun getKlines(
        symbol: String,
        interval: String = "1m",
        limit: Int = 100
    ): List<JsonObject> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/v3/klines") {
                parameter("symbol", symbol)
                parameter("interval", interval)
                parameter("limit", limit.coerceIn(1, 1000))
            }
            
            if (response.status == HttpStatusCode.OK) {
                val jsonText = response.bodyAsText()
                val jsonArray = json.parseToJsonElement(jsonText).jsonArray
                jsonArray.mapNotNull { it.jsonObject }
            } else {
                AppLogger.logger.w { "BinanceApiAdapter: Failed to fetch klines. Status: ${response.status}" }
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "BinanceApiAdapter: Error fetching klines for $symbol" }
            emptyList()
        }
    }
}

