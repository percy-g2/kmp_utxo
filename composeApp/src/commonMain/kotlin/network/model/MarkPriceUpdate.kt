package network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MarkPriceUpdate(
    @SerialName("e") val event: String,
    @SerialName("E") val eventTime: Long,
    @SerialName("s") val symbol: String,
    @SerialName("p") val price: String,
    @SerialName("i") val indexPrice: String,
    @SerialName("r") val rate: String,
    @SerialName("T") val timestamp: Long
)

@Serializable
data class Ticker(
    @SerialName("e") val eventType: String,           // Event type
    @SerialName("E") val eventTime: Long,             // Event time
    @SerialName("s") val symbol: String,              // Symbol
    @SerialName("p") val priceChange: String,         // Price change
    @SerialName("P") val priceChangePercent: String,  // Price change percent
    @SerialName("w") val weightedAvgPrice: String,    // Weighted average price
    @SerialName("c") val lastPrice: String,           // Last price
    @SerialName("Q") val lastQuantity: String,        // Last quantity
    @SerialName("o") val openPrice: String,           // Open price
    @SerialName("h") val highPrice: String,           // High price
    @SerialName("l") val lowPrice: String,            // Low price
    @SerialName("v") val totalTradedBaseAssetVolume: String,  // Total traded base asset volume
    @SerialName("q") val totalTradedQuoteAssetVolume: String, // Total traded quote asset volume
    @SerialName("O") val statisticsOpenTime: Long,    // Statistics open time
    @SerialName("C") val statisticsCloseTime: Long,   // Statistics close time
    @SerialName("F") val firstTradeId: Long,          // First trade ID
    @SerialName("L") val lastTradeId: Long,           // Last trade ID
    @SerialName("n") val totalNumberOfTrades: Long    // Total number of trades
)
