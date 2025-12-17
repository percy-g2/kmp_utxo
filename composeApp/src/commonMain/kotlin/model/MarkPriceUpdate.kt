package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Ticker(
    @SerialName("e") val eventType: String,           // Event type
    @SerialName("E") val eventTime: Long,             // Event time
    @SerialName("s") val symbol: String,              // Symbol
    @SerialName("p") val priceChange: String,         // Price change
    @SerialName("P") val priceChangePercent: String,  // Price change percent
    @SerialName("w") val weightedAvgPrice: String,    // Weighted average price
    @SerialName("x") val firstTradePrice: String,     // First trade(F)-1 price (first trade before the 24hr rolling window)
    @SerialName("c") val lastPrice: String,           // Last price
    @SerialName("Q") val lastQuantity: String,        // Last quantity
    @SerialName("b") val bestBidPrice: String,        // Best bid price
    @SerialName("B") val bestBidQuantity: String,     // Best bid quantity
    @SerialName("a") val bestAskPrice: String,        // Best ask price
    @SerialName("A") val bestAskQuantity: String,     // Best ask quantity
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

// REST API 24hr ticker response (different format from WebSocket)
@Serializable
data class Ticker24hr(
    @SerialName("symbol") val symbol: String,
    @SerialName("priceChange") val priceChange: String,
    @SerialName("priceChangePercent") val priceChangePercent: String,
    @SerialName("weightedAvgPrice") val weightedAvgPrice: String,
    @SerialName("prevClosePrice") val prevClosePrice: String,
    @SerialName("lastPrice") val lastPrice: String,
    @SerialName("lastQty") val lastQty: String,
    @SerialName("bidPrice") val bidPrice: String,
    @SerialName("bidQty") val bidQty: String,
    @SerialName("askPrice") val askPrice: String,
    @SerialName("askQty") val askQty: String,
    @SerialName("openPrice") val openPrice: String,
    @SerialName("highPrice") val highPrice: String,
    @SerialName("lowPrice") val lowPrice: String,
    @SerialName("volume") val volume: String,
    @SerialName("quoteVolume") val quoteVolume: String,
    @SerialName("openTime") val openTime: Long,
    @SerialName("closeTime") val closeTime: Long
)

@Serializable
data class BinanceError(
    @SerialName("code") val code: Int,
    @SerialName("msg") val msg: String
)


data class TickerData(
    val symbol: String,
    val lastPrice: String,
    val priceChangePercent: String,
    val volume: String
)

enum class SortParams {
    Pair,
    Vol,
    Price,
    Change
}
