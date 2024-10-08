package network

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import model.TickerDataInfo
import model.UiKline
import model.UiKlineSerializer

class HttpClient {
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

    private suspend fun fetchUiKline(symbol: String): List<UiKline> {
        return try {
            val response: HttpResponse = client.get("https://api.binance.com/api/v3/uiKlines") {
                parameter("symbol", symbol)
                parameter("interval", "1s")
                parameter("limit", 1000)
            }
            if (response.status == HttpStatusCode.OK) {
                return JsonConfig.json.decodeFromString(UiKlineSerializer, response.bodyAsText())
            } else emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // Return an empty list in case of an error
        }
    }

    suspend fun fetchBinancePairs(): List<TickerDataInfo> {
        return try {
            val response: HttpResponse = client.get("https://api.binance.com/api/v3/ticker/24hr")
            if (response.status == HttpStatusCode.OK) {
                val exchangeInfo = json.decodeFromString<List<TickerDataInfo>>(response.bodyAsText())

                return exchangeInfo
                    .filter { it.symbol.contains("USDT") && it.symbol.startsWith("USD").not() }
                    .sortedByDescending { it.quoteVolume.toDoubleOrNull() ?: 0.0 }
            } else emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // Return an empty list in case of an error
        }
    }

    suspend fun fetchUiKlines(symbols: List<String>): Map<String, List<UiKline>> = coroutineScope {
        val result = mutableMapOf<String, List<UiKline>>()
        val deferreds = symbols.map { symbol ->
            async { symbol to fetchUiKline(symbol) }
        }
        deferreds.forEach { deferred ->
            val (symbol, klines) = deferred.await()
            result[symbol] = klines
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
