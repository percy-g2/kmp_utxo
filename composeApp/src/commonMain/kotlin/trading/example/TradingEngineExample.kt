package trading.example

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json
import logging.AppLogger
import model.OrderBookData
import model.OrderBookLevel
import network.OrderBookWebSocketService
import trading.api.AggTradeWebSocketService
import trading.api.BinanceApiAdapter
import trading.config.StrategyConfig
import trading.data.AggTrade
import trading.data.MarketSnapshotBuilder
import trading.engine.TradingEngine
import trading.engine.TradingEngineFactory
import trading.execution.ExecutionResult
import trading.strategy.TradeFlowAnalyzer

/**
 * Example usage of the trading engine
 * 
 * This demonstrates how to:
 * 1. Set up the trading engine with all dependencies
 * 2. Connect to Binance WebSocket streams
 * 3. Process market updates and execute trades
 * 
 * NOTE: This is example code - modify as needed for your use case
 */
class TradingEngineExample {
    
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    private val config = StrategyConfig.DEFAULT
    private val equity = 10000.0  // $10,000 starting equity
    
    private lateinit var engine: TradingEngine
    private lateinit var orderBookService: OrderBookWebSocketService
    private lateinit var aggTradeService: AggTradeWebSocketService
    private lateinit var apiAdapter: BinanceApiAdapter
    private lateinit var snapshotBuilder: MarketSnapshotBuilder
    
    /**
     * Initialize the trading engine and all services
     */
    fun initialize() {
        // Create factory
        val factory = TradingEngineFactory(
            httpClient = httpClient,
            config = config,
            equity = equity
        )
        
        // Create trading engine
        engine = factory.create()
        
        // Create API adapter
        apiAdapter = factory.createApiAdapter()
        
        // Create WebSocket services
        orderBookService = OrderBookWebSocketService()
        aggTradeService = factory.createAggTradeWebSocket()
        
        // Create snapshot builder
        val tradeFlowAnalyzer = TradeFlowAnalyzer(
            confirmationThreshold = config.tradeFlowThreshold
        )
        snapshotBuilder = MarketSnapshotBuilder(tradeFlowAnalyzer)
        
        AppLogger.logger.i { "TradingEngineExample: Initialized with equity: $equity" }
    }
    
    /**
     * Start trading for a symbol
     * 
     * @param symbol Trading pair (e.g., "BTCUSDT")
     */
    suspend fun startTrading(symbol: String) {
        // Connect to WebSocket streams
        orderBookService.connect(symbol, levels = 20)
        aggTradeService.connect(symbol)
        
        AppLogger.logger.i { "TradingEngineExample: Started trading for $symbol" }
        
        // Collect order book updates
        orderBookService.orderBookData.collectLatest { orderBook ->
            if (orderBook != null) {
                // Get recent trades from WebSocket
                val recentTrades = aggTradeService.trades.value.take(500)
                
                // Build market snapshot
                val snapshot = snapshotBuilder.build(
                    symbol = symbol,
                    orderBook = orderBook,
                    trades = recentTrades
                )
                
                // Process market update through trading engine
                val result = engine.onMarketUpdate(snapshot)
                
                // Handle execution result
                when (result) {
                    is ExecutionResult.Success -> {
                        AppLogger.logger.i {
                            "Trade executed: ${result.orderId}, " +
                            "quantity: ${result.filledQuantity}, " +
                            "price: ${result.avgFillPrice}"
                        }
                    }
                    is ExecutionResult.Rejected -> {
                        AppLogger.logger.d { "Trade rejected: ${result.reason}" }
                    }
                    is ExecutionResult.Error -> {
                        AppLogger.logger.e(throwable = result.throwable) {
                            "Trade error: ${result.message}"
                        }
                    }
                    null -> {
                        // No trade - this is normal, most market updates don't result in trades
                    }
                    else -> {}
                }
            }
        }
    }
    
    /**
     * Stop trading and cleanup
     */
    fun stop() {
        orderBookService.disconnect()
        aggTradeService.disconnect()
        httpClient.close()
        AppLogger.logger.i { "TradingEngineExample: Stopped trading" }
    }
}

/**
 * Example of manual snapshot creation for testing
 */
object ManualSnapshotExample {
    
    fun createTestSnapshot(): trading.data.MarketSnapshot {
        // Create test order book
        val bids = listOf(
            OrderBookLevel("50000.00", "1.5"),
            OrderBookLevel("49999.00", "2.0"),
            OrderBookLevel("49998.00", "1.8")
        )
        
        val asks = listOf(
            OrderBookLevel("50001.00", "1.2"),
            OrderBookLevel("50002.00", "1.5"),
            OrderBookLevel("50003.00", "2.0")
        )
        
        val orderBook = OrderBookData(
            symbol = "BTCUSDT",
            bids = bids,
            asks = asks,
            lastUpdateId = 12345,
            timestamp = System.currentTimeMillis()
        )
        
        // Create test trades (aggressive buying)
        val trades = listOf(
            AggTrade(
                aggregateTradeId = 1,
                price = "50001.00",
                quantity = "0.5",
                firstTradeId = 1,
                lastTradeId = 1,
                timestamp = System.currentTimeMillis(),
                isBuyerMaker = false  // Aggressive buy
            ),
            AggTrade(
                aggregateTradeId = 2,
                price = "50001.50",
                quantity = "0.3",
                firstTradeId = 2,
                lastTradeId = 2,
                timestamp = System.currentTimeMillis() - 1000,
                isBuyerMaker = false  // Aggressive buy
            )
        )
        
        val tradeFlowAnalyzer = TradeFlowAnalyzer()
        val builder = MarketSnapshotBuilder(tradeFlowAnalyzer)
        
        return builder.build(
            symbol = "BTCUSDT",
            orderBook = orderBook,
            trades = trades
        )
    }
}

