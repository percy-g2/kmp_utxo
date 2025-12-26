package network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import logging.AppLogger
import model.MarginSymbols
import model.Ticker
import model.UiKline
import model.UiKlineSerializer
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class RateLimiter(
    private val maxRequests: Int,
    private val windowDurationMillis: Long
) {
    private val isLocked = atomic(false)
    @OptIn(ExperimentalTime::class)
    private val requests = mutableListOf<Instant>()

    @OptIn(ExperimentalTime::class)
    suspend fun acquire() {
        while (true) {
            val now = kotlin.time.Clock.System.now()
            val windowStart: Instant = now.minus(windowDurationMillis.milliseconds)

            while (!isLocked.compareAndSet(expect = false, update = true)) {
                delay(1)
            }

            try {
                requests.removeAll { it < windowStart }
                if (requests.size < maxRequests) {
                    requests.add(now)
                    return
                }
            } finally {
                isLocked.value = false
            }

            delay(50)
        }
    }
}

class HttpClient {
    private val rateLimiter = RateLimiter(
        maxRequests = 10,
        windowDurationMillis = 1000 // 1 second
    )

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client = HttpClient {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.NONE
        }
        install(ContentNegotiation) {
            json(json)
        }
    }
    
    fun close() {
        client.close()
    }

    private suspend fun fetchUiKline(symbol: String, maxRetries: Int = 3): List<UiKline> {
        for (attempt in 0 until maxRetries) {
            try {
                val response: HttpResponse = client.get("https://api.binance.com/api/v3/uiKlines") {
                    parameter("symbol", symbol)
                    parameter("interval", "1s")
                    parameter("limit", 1000)
                }

                when {
                    response.status == HttpStatusCode.OK -> {
                        return JsonConfig.json.decodeFromString(UiKlineSerializer, response.bodyAsText())
                    }
                    response.status.value in 500..599 -> {
                        delay(1000L * (attempt + 1)) // Exponential backoff
                        continue
                    }
                    else -> return emptyList()
                }
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    AppLogger.logger.e(throwable = e) { "Failed to fetch UI klines for symbol $symbol after $maxRetries attempts" }
                    return emptyList()
                }
                delay(1000L * (attempt + 1))
            }
        }
        return emptyList()
    }

    /**
     * Fetch historical klines for a symbol with a specific interval
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     * @param interval Kline interval (e.g., "1m", "5m", "15m", "1h", "4h", "1d")
     * @param limit Maximum number of klines to return (default: 500)
     * @return List of UiKline objects
     */
    suspend fun fetchKlines(symbol: String, interval: String, limit: Int = 500): List<UiKline> {
        return try {
            rateLimiter.acquire()
            val response: HttpResponse = client.get("https://api.binance.com/api/v3/klines") {
                parameter("symbol", symbol)
                parameter("interval", interval)
                parameter("limit", limit)
            }

            when {
                response.status == HttpStatusCode.OK -> {
                    JsonConfig.json.decodeFromString(UiKlineSerializer, response.bodyAsText())
                }
                else -> {
                    AppLogger.logger.w { "Binance API returned status ${response.status} for klines $symbol@$interval" }
                    emptyList()
                }
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "Error fetching klines for symbol $symbol@$interval" }
            emptyList()
        }
    }

    suspend fun fetchMarginSymbols(): MarginSymbols? {
        return try {
            val response: HttpResponse = client.get("https://www.binance.com/bapi/margin/v1/public/margin/symbols") {
                headers {
                    append("Accept-Encoding", "identity")
                    append("User-Agent", "Mozilla/5.0")
                }
            }
            if (response.status == HttpStatusCode.OK) {
                json.decodeFromString<MarginSymbols>(response.bodyAsText())
            } else null
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "Error fetching margin symbols" }
            null
        }
    }

    suspend fun fetchUiKlines(symbols: List<String>): Map<String, List<UiKline>> = coroutineScope {
        val result = mutableMapOf<String, List<UiKline>>()

        // Process in smaller batches
        symbols.chunked(5).forEach { batch ->
            val deferreds = batch.map { symbol ->
                async {
                    rateLimiter.acquire() // Wait for rate limit
                    symbol to fetchUiKline(symbol)
                }
            }

            deferreds.forEach { deferred ->
                val (symbol, klines) = deferred.await()
                result[symbol] = klines
            }
        }

        result
    }
    
    suspend fun fetchTicker24hr(symbol: String): model.Ticker24hr? {
        return try {
            rateLimiter.acquire()
            val response: HttpResponse = client.get("https://api.binance.com/api/v3/ticker/24hr") {
                parameter("symbol", symbol)
            }
            val responseText = response.bodyAsText()
            
            if (response.status == HttpStatusCode.OK) {
                // Check if response is an error object
                if (responseText.contains("\"code\"") && responseText.contains("\"msg\"")) {
                    try {
                        val error = json.decodeFromString<model.BinanceError>(responseText)
                        AppLogger.logger.w { "Binance API error for symbol $symbol: Code ${error.code}, Message: ${error.msg}" }
                    } catch (e: Exception) {
                        AppLogger.logger.w { "Binance API error for symbol $symbol: $responseText" }
                    }
                    return null
                }
                // Try to deserialize as Ticker24hr (REST API format)
                try {
                    json.decodeFromString<model.Ticker24hr>(responseText)
                } catch (e: kotlinx.serialization.SerializationException) {
                    AppLogger.logger.e(throwable = e) { 
                        "Failed to deserialize ticker for symbol $symbol. Response was (first 500 chars): ${responseText.take(500)}" 
                    }
                    null
                }
            } else {
                AppLogger.logger.w { "Binance API returned status ${response.status} for symbol $symbol: ${responseText.take(200)}" }
                null
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "Error fetching ticker for symbol $symbol" }
            null
        }
    }
    
    suspend fun fetchTickers24hr(symbols: List<String>): Map<String, model.Ticker24hr> = coroutineScope {
        val result = mutableMapOf<String, model.Ticker24hr>()
        
        // Process in smaller batches to respect rate limits
        symbols.chunked(5).forEach { batch ->
            val deferreds = batch.map { symbol ->
                async {
                    fetchTicker24hr(symbol)?.let { symbol to it }
                }
            }
            
            deferreds.forEach { deferred ->
                deferred.await()?.let { (symbol, ticker) ->
                    result[symbol] = ticker
                }
            }
        }
        
        result
    }
}

object JsonConfig {
    val json: Json = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            contextual(UiKlineSerializer)
        }
    }
}
