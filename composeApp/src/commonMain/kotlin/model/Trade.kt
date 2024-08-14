package model

import kotlinx.serialization.Serializable

@Serializable
data class Trade(
    val id: Long = 0L,
    val price: String,
    val qty: String? = null,
    val quoteQty: String? = null,
    val time: Long? = null,
    val isBuyerMaker: Boolean? = null,
    val isBestMatch: Boolean? = null
)