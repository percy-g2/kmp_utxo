package com.percy.utxo.network.model

import kotlinx.serialization.Serializable

@Serializable
data class Trade(
    val id: Long,
    val price: String,
    val qty: String,
    val quoteQty: String,
    val time: Long,
    val isBuyerMaker: Boolean,
    val isBestMatch: Boolean
)