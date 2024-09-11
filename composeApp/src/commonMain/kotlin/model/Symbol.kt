package model

import kotlinx.serialization.Serializable

@Serializable
data class TickerDataInfo(
    val symbol: String,
    val quoteVolume: String
)