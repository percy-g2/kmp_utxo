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
import model.MarginSymbols
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
                    e.printStackTrace()
                    return emptyList()
                }
                delay(1000L * (attempt + 1))
            }
        }
        return emptyList()
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
            e.printStackTrace()
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
}

object JsonConfig {
    val json: Json = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            contextual(UiKlineSerializer)
        }
    }
}
