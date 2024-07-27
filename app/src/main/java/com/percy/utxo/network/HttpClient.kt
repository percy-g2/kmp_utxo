package com.percy.utxo.network

import com.percy.utxo.network.model.Trade
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class HttpClient {
    val client = HttpClient(CIO) {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                }
            )
        }
    }

    suspend fun fetchBtcTrades(): List<Trade> {
        val json = Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }

        return try {
            json.decodeFromString<List<Trade>>(client.get("https://api.binance.com/api/v3/trades?symbol=BTCUSDT").bodyAsText())
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // Return an empty list in case of an error
        }
    }
}