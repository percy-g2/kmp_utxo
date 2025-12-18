package trading.data

import model.OrderBookData
import model.OrderBookLevel

/**
 * Enhanced order book utilities for trading engine
 * Extends existing OrderBookData with trading-specific calculations
 */
object OrderBook {
    
    /**
     * Calculates volume-weighted order book imbalance
     * 
     * WHY: Simple bid/ask volume ratio doesn't account for price levels.
     * Volume-weighted imbalance gives more weight to levels closer to mid price.
     * 
     * Formula: bidVolume / askVolume
     * - > 1.5 indicates strong buy pressure (LONG candidate)
     * - < 0.67 indicates strong sell pressure (SHORT candidate)
     * 
     * @param orderBook Order book data
     * @param topN Number of levels to analyze (default 20)
     * @return Imbalance ratio (bidVolume / askVolume)
     */
    fun calculateImbalance(orderBook: OrderBookData, topN: Int = 20): Double {
        val bids = orderBook.bids.take(topN)
        val asks = orderBook.asks.take(topN)
        
        // Calculate volume-weighted bid depth
        val bidVolume = bids.sumOf { level ->
            // Weight by proximity to best bid (closer = higher weight)
            val bestBid = bids.firstOrNull()?.priceDouble ?: return 0.0
            val weight = if (bestBid > 0) level.priceDouble / bestBid else 1.0
            level.quantityDouble * weight
        }
        
        // Calculate volume-weighted ask depth
        val askVolume = asks.sumOf { level ->
            // Weight by proximity to best ask (closer = higher weight)
            val bestAsk = asks.firstOrNull()?.priceDouble ?: return 0.0
            val weight = if (bestAsk > 0) level.priceDouble / bestAsk else 1.0
            level.quantityDouble * weight
        }
        
        // Return imbalance ratio
        return if (askVolume > 0.0) bidVolume / askVolume else 1.0
    }
    
    /**
     * Calculates total quote currency depth (USD value) for bids
     * WHY: Needed for position sizing - ensures we don't exceed available liquidity
     */
    fun calculateBidDepthUSD(orderBook: OrderBookData, topN: Int = 20): Double {
        return orderBook.bids.take(topN).sumOf { it.priceDouble * it.quantityDouble }
    }
    
    /**
     * Calculates total quote currency depth (USD value) for asks
     * WHY: Needed for position sizing - ensures we don't exceed available liquidity
     */
    fun calculateAskDepthUSD(orderBook: OrderBookData, topN: Int = 20): Double {
        return orderBook.asks.take(topN).sumOf { it.priceDouble * it.quantityDouble }
    }
    
    /**
     * Finds the price level that would absorb a given quote currency amount
     * WHY: Calculates realistic fill price accounting for order book depth
     * 
     * @param orderBook Order book data
     * @param quoteAmount Amount in quote currency (e.g., USDT)
     * @param isBuy True for buy orders, false for sell orders
     * @return Average fill price, or null if depth insufficient
     */
    fun calculateAverageFillPrice(
        orderBook: OrderBookData,
        quoteAmount: Double,
        isBuy: Boolean
    ): Double? {
        if (quoteAmount <= 0.0) return null
        
        var remainingAmount = quoteAmount
        var totalBaseQuantity = 0.0
        var totalCost = 0.0
        
        val levels = if (isBuy) orderBook.asks else orderBook.bids.reversed()
        
        for (level in levels) {
            val levelValue = level.priceDouble * level.quantityDouble
            
            if (remainingAmount <= levelValue) {
                // Partial fill at this level
                val fillQuantity = remainingAmount / level.priceDouble
                totalBaseQuantity += fillQuantity
                totalCost += remainingAmount
                remainingAmount = 0.0
                break
            } else {
                // Full fill at this level
                totalBaseQuantity += level.quantityDouble
                totalCost += levelValue
                remainingAmount -= levelValue
            }
        }
        
        // If we couldn't fill the entire order, return null (insufficient depth)
        if (remainingAmount > 0.0) return null
        
        return if (totalBaseQuantity > 0.0) totalCost / totalBaseQuantity else null
    }
    
    /**
     * Checks if order book has sufficient depth to absorb a trade
     * WHY: Prevents slippage by ensuring adequate liquidity
     * 
     * @param orderBook Order book data
     * @param quoteAmount Amount in quote currency
     * @param isBuy True for buy orders
     * @param minDepthPct Minimum depth as percentage of order size (default 2%)
     * @return True if depth is sufficient
     */
    fun hasSufficientDepth(
        orderBook: OrderBookData,
        quoteAmount: Double,
        isBuy: Boolean,
        minDepthPct: Double = 0.02
    ): Boolean {
        val requiredDepth = quoteAmount * (1.0 + minDepthPct)
        val availableDepth = if (isBuy) {
            calculateAskDepthUSD(orderBook)
        } else {
            calculateBidDepthUSD(orderBook)
        }
        
        return availableDepth >= requiredDepth
    }
}

