package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single order book level (price and quantity)
 */
data class OrderBookLevel(
    val price: String,
    val quantity: String
) {
    val priceDouble: Double get() = price.toDoubleOrNull() ?: 0.0
    val quantityDouble: Double get() = quantity.toDoubleOrNull() ?: 0.0
}

/**
 * Order book depth update from Binance WebSocket Stream
 * Format: <symbol>@depth<levels> or <symbol>@depth
 * This is the incremental update format
 */
@Serializable
data class OrderBookDepthUpdate(
    @SerialName("e") val eventType: String, // Event type (e.g., "depthUpdate")
    @SerialName("E") val eventTime: Long,  // Event time
    @SerialName("s") val symbol: String,    // Symbol
    @SerialName("U") val firstUpdateId: Long, // First update ID in event
    @SerialName("u") val finalUpdateId: Long, // Final update ID in event
    @SerialName("b") val bids: List<List<String>>, // Bid levels [price, quantity]
    @SerialName("a") val asks: List<List<String>>  // Ask levels [price, quantity]
)

/**
 * Order book depth snapshot from Binance WebSocket Stream
 * First message sent when connecting to depth stream
 */
@Serializable
data class OrderBookDepthSnapshot(
    @SerialName("lastUpdateId") val lastUpdateId: Long,
    @SerialName("bids") val bids: List<List<String>>, // Bid levels [price, quantity]
    @SerialName("asks") val asks: List<List<String>>  // Ask levels [price, quantity]
)

/**
 * Processed order book data for UI display
 */
data class OrderBookData(
    val symbol: String,
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>,
    val lastUpdateId: Long,
    val timestamp: Long
) {
    val bestBid: OrderBookLevel? get() = bids.firstOrNull()
    val bestAsk: OrderBookLevel? get() = asks.firstOrNull()
    
    val midPrice: Double? get() {
        val bid = bestBid?.priceDouble ?: return null
        val ask = bestAsk?.priceDouble ?: return null
        return (bid + ask) / 2.0
    }
}

