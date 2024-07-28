package network

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import network.model.Trade
import network.model.UiKline
import network.model.UiKlineSerializer

class HttpClient {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client = HttpClient {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun fetchBtcTrades(): List<Trade> {
        return try {
            json.decodeFromString<List<Trade>>(client.get("https://api.binance.com/api/v3/historicalTrades?symbol=BTCUSDT").bodyAsText())
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // Return an empty list in case of an error
        }
    }

    suspend fun fetchUiKline(): List<UiKline> {
        return try {
            val response: HttpResponse = client.get("https://api.binance.com/api/v3/uiKlines") {
                parameter("symbol", "BTCUSDT")
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
}

object JsonConfig {
    val json: Json = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            contextual(UiKlineSerializer)
        }
    }
}
