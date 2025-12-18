package trading.engine

import io.ktor.client.HttpClient
import trading.api.AggTradeWebSocketService
import trading.api.BinanceApiAdapter
import trading.config.StrategyConfig
import trading.execution.ExecutionPolicy
import trading.execution.MockOrderExecutor
import trading.execution.OrderExecutor
import trading.risk.DailyLossGuard
import trading.risk.RiskManager
import trading.strategy.ImbalanceCalculator
import trading.strategy.PositionSizer
import trading.strategy.SpreadFilter
import trading.strategy.TradeFlowAnalyzer

/**
 * Factory for creating TradingEngine instances with all dependencies
 * 
 * WHY: Simplifies engine setup and ensures proper dependency injection
 */
class TradingEngineFactory(
    private val httpClient: HttpClient,
    private val config: StrategyConfig = StrategyConfig.DEFAULT,
    private val equity: Double,
    private val orderExecutor: OrderExecutor? = null  // Use mock by default for safety
) {
    
    /**
     * Creates a fully configured TradingEngine
     */
    fun create(): TradingEngine {
        // Risk management
        val dailyLossGuard = DailyLossGuard(
            maxDailyLossPct = config.maxDailyLossPct,
            startingEquity = equity
        )
        val riskManager = RiskManager(
            dailyLossGuard = dailyLossGuard,
            maxConsecutiveLosses = config.maxConsecutiveLosses,
            cooldownAfterLossesMs = config.cooldownAfterLossesMs,
            maxVolatilityPct = config.maxVolatilityPct
        )
        
        // Strategy components
        val spreadFilter = SpreadFilter(
            maxSpreadPct = config.maxSpreadPct,
            minDepthBufferPct = config.minDepthBufferPct
        )
        
        val imbalanceCalculator = ImbalanceCalculator(
            longThreshold = config.imbalanceLong,
            shortThreshold = config.imbalanceShort,
            topNLevels = config.topNLevels
        )
        
        val tradeFlowAnalyzer = TradeFlowAnalyzer(
            confirmationThreshold = config.tradeFlowThreshold
        )
        
        val positionSizer = PositionSizer(
            maxDepthPct = config.maxDepthPct,
            maxRiskPerTradePct = config.maxRiskPerTradePct,
            slippageBufferPct = config.slippageBufferPct,
            feePct = config.feePct
        )
        
        val executionPolicy = ExecutionPolicy(
            preferMaker = config.preferMaker,
            makerSpreadThreshold = config.makerSpreadThreshold,
            momentumThreshold = config.momentumThreshold
        )
        
        // Order executor (use mock by default for safety)
        val executor = orderExecutor ?: MockOrderExecutor()
        
        return TradingEngine(
            riskManager = riskManager,
            spreadFilter = spreadFilter,
            imbalanceCalculator = imbalanceCalculator,
            tradeFlowAnalyzer = tradeFlowAnalyzer,
            positionSizer = positionSizer,
            executionPolicy = executionPolicy,
            orderExecutor = executor,
            equity = equity
        )
    }
    
    /**
     * Creates Binance API adapter
     */
    fun createApiAdapter(): BinanceApiAdapter {
        return BinanceApiAdapter(httpClient)
    }
    
    /**
     * Creates aggregated trades WebSocket service
     */
    fun createAggTradeWebSocket(): AggTradeWebSocketService {
        return AggTradeWebSocketService()
    }
}

