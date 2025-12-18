package trading.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Aggregated trade data from Binance aggTrades endpoint
 * Represents a single aggregated trade (multiple individual trades combined)
 * 
 * Used for trade flow analysis to detect aggressive buying/selling pressure
 */
@Serializable
data class AggTrade(
    @SerialName("a") val aggregateTradeId: Long,  // Aggregate trade ID
    @SerialName("p") val price: String,           // Price
    @SerialName("q") val quantity: String,        // Quantity
    @SerialName("f") val firstTradeId: Long,      // First trade ID
    @SerialName("l") val lastTradeId: Long,       // Last trade ID
    @SerialName("T") val timestamp: Long,         // Trade timestamp
    @SerialName("m") val isBuyerMaker: Boolean    // Was buyer the maker (false = aggressive buyer)
) {
    val priceDouble: Double get() = price.toDoubleOrNull() ?: 0.0
    val quantityDouble: Double get() = quantity.toDoubleOrNull() ?: 0.0
    
    /**
     * Returns true if this trade represents aggressive buying (taker was buyer)
     * When isBuyerMaker=false, the buyer was the taker (aggressive buy)
     */
    val isAggressiveBuy: Boolean get() = !isBuyerMaker
    
    /**
     * Returns true if this trade represents aggressive selling (taker was seller)
     * When isBuyerMaker=true, the seller was the taker (aggressive sell)
     */
    val isAggressiveSell: Boolean get() = isBuyerMaker
    
    /**
     * Trade value in quote currency (price * quantity)
     */
    val tradeValue: Double get() = priceDouble * quantityDouble
}

/**
 * Trade flow metrics calculated from a series of aggregated trades
 * Used to confirm order book imbalance direction
 */
data class TradeFlowMetrics(
    val aggressiveBuyVolume: Double,      // Total volume from aggressive buys
    val aggressiveSellVolume: Double,     // Total volume from aggressive sells
    val totalVolume: Double,               // Total volume
    val buyPressureRatio: Double,         // aggressiveBuyVolume / aggressiveSellVolume
    val sellPressureRatio: Double,        // aggressiveSellVolume / aggressiveBuyVolume
    val sampleCount: Int,                 // Number of trades analyzed
    val timeWindowMs: Long                // Time window in milliseconds
) {
    /**
     * Returns true if buy pressure significantly exceeds sell pressure
     * Threshold: buyPressureRatio > 1.5
     */
    fun hasStrongBuyFlow(threshold: Double = 1.5): Boolean {
        return buyPressureRatio > threshold && aggressiveBuyVolume > 0.0
    }
    
    /**
     * Returns true if sell pressure significantly exceeds buy pressure
     * Threshold: sellPressureRatio > 1.5
     */
    fun hasStrongSellFlow(threshold: Double = 1.5): Boolean {
        return sellPressureRatio > threshold && aggressiveSellVolume > 0.0
    }
    
    /**
     * Returns true if trade flow confirms a LONG signal (strong buy flow)
     */
    fun confirmsLong(): Boolean = hasStrongBuyFlow()
    
    /**
     * Returns true if trade flow confirms a SHORT signal (strong sell flow)
     */
    fun confirmsShort(): Boolean = hasStrongSellFlow()
}

