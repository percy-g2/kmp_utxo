package trading.data

import model.OrderBookData
import kotlin.time.ExperimentalTime

/**
 * Complete market snapshot combining order book, trade flow, and market data
 * This is the primary input for trading strategy evaluation
 * 
 * WHY: Consolidates all market data needed for decision-making in a single immutable snapshot
 */
@OptIn(ExperimentalTime::class)
data class MarketSnapshot(
    val symbol: String,
    val orderBook: OrderBookData,
    val tradeFlow: TradeFlowMetrics,
    val bestBid: Double,
    val bestAsk: Double,
    val midPrice: Double,
    val spread: Double,                    // Absolute spread (ask - bid)
    val spreadPct: Double,                 // Spread as percentage of mid price
    val timestamp: Long                    // Snapshot timestamp
) {
    /**
     * Returns true if market data is stale (older than threshold)
     * WHY: Prevents trading on outdated information
     */
    fun isStale(maxAgeMs: Long = 5000): Boolean {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        return (now - timestamp) > maxAgeMs
    }
    
    /**
     * Calculates total visible depth on bid side (top N levels)
     */
    fun getBidDepth(topN: Int = 20): Double {
        return orderBook.bids.take(topN).sumOf { it.quantityDouble * it.priceDouble }
    }
    
    /**
     * Calculates total visible depth on ask side (top N levels)
     */
    fun getAskDepth(topN: Int = 20): Double {
        return orderBook.asks.take(topN).sumOf { it.quantityDouble * it.priceDouble }
    }
    
    /**
     * Calculates volume-weighted average price for bids (top N levels)
     */
    fun getVWAPBid(topN: Int = 20): Double {
        val levels = orderBook.bids.take(topN)
        val totalValue = levels.sumOf { it.priceDouble * it.quantityDouble }
        val totalVolume = levels.sumOf { it.quantityDouble }
        return if (totalVolume > 0.0) totalValue / totalVolume else bestBid
    }
    
    /**
     * Calculates volume-weighted average price for asks (top N levels)
     */
    fun getVWAPAsk(topN: Int = 20): Double {
        val levels = orderBook.asks.take(topN)
        val totalValue = levels.sumOf { it.priceDouble * it.quantityDouble }
        val totalVolume = levels.sumOf { it.quantityDouble }
        return if (totalVolume > 0.0) totalValue / totalVolume else bestAsk
    }
}

