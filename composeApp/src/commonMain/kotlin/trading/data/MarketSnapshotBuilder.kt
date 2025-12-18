package trading.data

import model.OrderBookData
import trading.api.BinanceApiAdapter
import trading.strategy.TradeFlowAnalyzer

/**
 * Builder for creating MarketSnapshot from various data sources
 * 
 * WHY: Simplifies creation of MarketSnapshot from API data
 */
class MarketSnapshotBuilder(
    private val tradeFlowAnalyzer: TradeFlowAnalyzer
) {
    
    /**
     * Builds MarketSnapshot from order book and trade flow data
     * 
     * @param symbol Trading pair symbol
     * @param orderBook Order book data
     * @param trades List of aggregated trades (most recent first)
     * @param timeWindowMs Time window for trade flow analysis
     * @return Market snapshot
     */
    fun build(
        symbol: String,
        orderBook: OrderBookData,
        trades: List<AggTrade>,
        timeWindowMs: Long = 5000
    ): MarketSnapshot {
        val bestBid = orderBook.bestBid?.priceDouble ?: 0.0
        val bestAsk = orderBook.bestAsk?.priceDouble ?: 0.0
        val midPrice = (bestBid + bestAsk) / 2.0
        val spread = bestAsk - bestBid
        val spreadPct = if (midPrice > 0.0) spread / midPrice else 0.0
        
        val tradeFlow = tradeFlowAnalyzer.calculateMetrics(trades, timeWindowMs)
        
        return MarketSnapshot(
            symbol = symbol,
            orderBook = orderBook,
            tradeFlow = tradeFlow,
            bestBid = bestBid,
            bestAsk = bestAsk,
            midPrice = midPrice,
            spread = spread,
            spreadPct = spreadPct,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Builds MarketSnapshot from Binance API data
     * 
     * Convenience method that fetches all required data
     */
    suspend fun buildFromApi(
        symbol: String,
        apiAdapter: BinanceApiAdapter,
        orderBookLimit: Int = 20,
        tradeLimit: Int = 500
    ): MarketSnapshot? {
        // Fetch order book
        val orderBook = apiAdapter.getDepth(symbol, orderBookLimit) ?: return null
        
        // Fetch aggregated trades
        val trades = apiAdapter.getAggTrades(symbol, tradeLimit)
        
        return build(symbol, orderBook, trades)
    }
}

