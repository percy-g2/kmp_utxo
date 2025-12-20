package org.androdevlinux.utxo.widget.helper

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import model.Ticker24hr
import java.net.HttpURLConnection
import java.net.URL

object TickerDataHelper {
    fun fetchTickers(
        context: Context,
        symbols: List<String>,
        json: Json
    ): Map<String, TickerData> {
        return runBlocking {
            symbols.associateWith { symbol ->
                fetchTicker(symbol, json)
            }.filterValues { it != null }.mapValues { it.value!! }
        }
    }

    private suspend fun fetchTicker(symbol: String, json: Json): TickerData? {
        return try {
            val url = URL("https://api.binance.com/api/v3/ticker/24hr?symbol=$symbol")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Check if response is an error
                if (response.contains("\"code\"") && response.contains("\"msg\"")) {
                    return null
                }
                
                val ticker24hr = json.decodeFromString<Ticker24hr>(response)
                TickerData(
                    symbol = ticker24hr.symbol,
                    lastPrice = ticker24hr.lastPrice,
                    priceChangePercent = ticker24hr.priceChangePercent,
                    volume = ticker24hr.quoteVolume
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    data class TickerData(
        val symbol: String,
        val lastPrice: String,
        val priceChangePercent: String,
        val volume: String
    )
}

