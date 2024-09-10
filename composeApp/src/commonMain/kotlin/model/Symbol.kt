package model

import kotlinx.serialization.Serializable

@Serializable
data class TickerDataInfo(
    val symbol: String,
    val priceChange: String,
    val quoteVolume: String
)