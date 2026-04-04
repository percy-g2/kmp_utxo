package model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Ticker(
    @SerialName("e") val eventType: String,
    @SerialName("E") val eventTime: Long,
    @SerialName("s") val symbol: String,
    @SerialName("p") val priceChange: String,
    @SerialName("P") val priceChangePercent: String,
    @SerialName("w") val weightedAvgPrice: String,
    @SerialName("x") val firstTradePrice: String,
    @SerialName("c") val lastPrice: String,
    @SerialName("Q") val lastQuantity: String,
    @SerialName("b") val bestBidPrice: String,
    @SerialName("B") val bestBidQuantity: String,
    @SerialName("a") val bestAskPrice: String,
    @SerialName("A") val bestAskQuantity: String,
    @SerialName("o") val openPrice: String,
    @SerialName("h") val highPrice: String,
    @SerialName("l") val lowPrice: String,
    @SerialName("v") val totalTradedBaseAssetVolume: String,
    @SerialName("q") val totalTradedQuoteAssetVolume: String,
    @SerialName("O") val statisticsOpenTime: Long,
    @SerialName("C") val statisticsCloseTime: Long,
    @SerialName("F") val firstTradeId: Long,
    @SerialName("L") val lastTradeId: Long,
    @SerialName("n") val totalNumberOfTrades: Long,
)

/** Binance `!miniTicker@arr` / 24hrMiniTicker WebSocket payload (replaces retired `!ticker@arr`). */
@Serializable
data class MiniTicker(
    @SerialName("e") val eventType: String,
    @SerialName("E") val eventTime: Long,
    @SerialName("s") val symbol: String,
    @SerialName("c") val closePrice: String,
    @SerialName("o") val openPrice: String,
    @SerialName("h") val highPrice: String,
    @SerialName("l") val lowPrice: String,
    @SerialName("v") val totalTradedBaseAssetVolume: String,
    @SerialName("q") val totalTradedQuoteAssetVolume: String,
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
    @SerialName("closeTime") val closeTime: Long,
)

@Serializable
data class BinanceError(
    @SerialName("code") val code: Int,
    @SerialName("msg") val msg: String,
)

@Immutable
data class TickerData(
    val symbol: String,
    val lastPrice: String,
    val priceChangePercent: String,
    val volume: String,
)

enum class SortParams {
    Pair,
    Vol,
    Price,
    Change,
}
