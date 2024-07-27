package com.percy.utxo.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MarkPriceUpdate(
    @SerialName("e") val event: String,
    @SerialName("E") val eventTime: Long,
    @SerialName("s") val symbol: String,
    @SerialName("p") val price: String,
    @SerialName("P") val avgPrice: String,
    @SerialName("i") val indexPrice: String,
    @SerialName("r") val rate: String,
    @SerialName("T") val timestamp: Long
)