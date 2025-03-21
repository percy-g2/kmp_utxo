package model

import kotlinx.serialization.Serializable

@Serializable
data class TickerDataInfo(
    val symbol: String,
    val quoteVolume: String,
    val quote: String
)


@Serializable
data class MarginSymbols(
    val code: String,
    val message: String? = null,
    val messageDetail: String? = null,
    val data: List<TradingPair>
)

@Serializable
data class TradingPair(
    val id: String,
    val symbol: String,
    val base: String,
    val quote: String,
    val isMarginTrade: Boolean,
    val isBuyAllowed: Boolean,
    val isSellAllowed: Boolean,
    val status: String,
    val delistedTime: String? = null
)