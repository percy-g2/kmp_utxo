package trading.engine

import logging.AppLogger
import trading.data.MarketSnapshot
import trading.execution.ExecutionPolicy
import trading.execution.ExecutionResult
import trading.execution.OrderExecutor
import trading.execution.OrderType
import trading.risk.RiskManager
import trading.strategy.ImbalanceCalculator
import trading.strategy.PositionSizer
import trading.strategy.SpreadFilter
import trading.strategy.TradeFlowAnalyzer
import trading.strategy.TradeSignal

/**
 * Main trading engine orchestrating all components
 * 
 * WHY: Central coordinator ensuring strict adherence to trading rules.
 * A trade is placed ONLY if ALL conditions are met:
 * 1. Risk manager approves
 * 2. Spread filter passes
 * 3. Order book imbalance exists
 * 4. Trade flow confirms direction
 * 5. Position size fits depth
 * 
 * Otherwise → NO TRADE
 */
class TradingEngine(
    private val riskManager: RiskManager,
    private val spreadFilter: SpreadFilter,
    private val imbalanceCalculator: ImbalanceCalculator,
    private val tradeFlowAnalyzer: TradeFlowAnalyzer,
    private val positionSizer: PositionSizer,
    private val executionPolicy: ExecutionPolicy,
    private val orderExecutor: OrderExecutor,
    private val equity: Double
) {
    
    /**
     * Main entry point - processes market update and potentially executes trade
     * 
     * WHY: Single entry point ensures consistent execution flow
     * 
     * Flow:
     * 1. Check risk manager → RETURN if blocked
     * 2. Check spread filter → RETURN if fails
     * 3. Evaluate strategy (imbalance + trade flow) → RETURN if no signal
     * 4. Calculate position size → RETURN if too small
     * 5. Execute order
     * 
     * @param snapshot Market snapshot with order book and trade flow data
     * @return Execution result or null if no trade
     */
    suspend fun onMarketUpdate(snapshot: MarketSnapshot): ExecutionResult? {
        // Step 1: Risk check (HARD RULE - must pass)
        if (!riskManager.canTrade(snapshot)) {
            AppLogger.logger.d { 
                "TradingEngine: Trade rejected by risk manager for ${snapshot.symbol}" 
            }
            return null
        }
        
        // Step 2: Spread and liquidity filter
        // We need to estimate position size first for depth check
        val estimatedSize = estimatePositionSize(snapshot)
        if (estimatedSize <= 0.0) {
            AppLogger.logger.d { 
                "TradingEngine: Position size too small for ${snapshot.symbol}" 
            }
            return null
        }
        
        val rejectionReason = spreadFilter.getRejectionReason(
            snapshot,
            estimatedSize,
            isBuy = true  // Will be determined by signal
        )
        
        if (rejectionReason != null) {
            AppLogger.logger.d { 
                "TradingEngine: Trade rejected by spread filter for ${snapshot.symbol}: $rejectionReason" 
            }
            return null
        }
        
        // Step 3: Evaluate strategy (imbalance + trade flow)
        val signal = evaluateStrategy(snapshot)
        if (!signal.isActionable()) {
            AppLogger.logger.d { 
                "TradingEngine: No actionable signal for ${snapshot.symbol}" 
            }
            return null
        }
        
        // Step 4: Re-check spread filter with actual signal direction
        val isBuy = signal.isLong()
        val actualRejectionReason = spreadFilter.getRejectionReason(
            snapshot,
            estimatedSize,
            isBuy
        )
        
        if (actualRejectionReason != null) {
            AppLogger.logger.d { 
                "TradingEngine: Trade rejected by spread filter (final check): $actualRejectionReason" 
            }
            return null
        }
        
        // Step 5: Calculate final position size
        val stopLossPct = when (signal) {
            is TradeSignal.Long -> signal.stopLoss?.let { stopLoss ->
                val entryPrice = signal.entryPrice
                (entryPrice - stopLoss) / entryPrice
            }
            is TradeSignal.Short -> signal.stopLoss?.let { stopLoss ->
                val entryPrice = signal.entryPrice
                (stopLoss - entryPrice) / entryPrice
            }
            else -> null
        }
        
        val positionSize = positionSizer.calculate(
            snapshot,
            equity,
            isBuy,
            stopLossPct
        )
        
        if (positionSize <= 0.0) {
            AppLogger.logger.d { 
                "TradingEngine: Position size calculation failed for ${snapshot.symbol}" 
            }
            return null
        }
        
        // Step 6: Determine execution parameters
        val orderType = executionPolicy.determineOrderType(
            signal,
            snapshot.spreadPct,
            momentum = calculateMomentum(snapshot)
        )
        
        val limitPrice = when (orderType) {
            OrderType.LIMIT_MAKER -> {
                executionPolicy.calculateMakerPrice(
                    signal,
                    snapshot.bestBid,
                    snapshot.bestAsk
                )
            }
            OrderType.LIMIT_TAKER -> {
                executionPolicy.calculateTakerPrice(
                    signal,
                    snapshot.bestBid,
                    snapshot.bestAsk
                )
            }
            OrderType.MARKET -> null
        }
        
        // Step 7: Calculate base quantity
        val entryPrice = signal.getEntryPrice() ?: snapshot.midPrice
        val quantity = positionSizer.calculateBaseQuantity(positionSize, entryPrice)
        val adjustedQuantity = positionSizer.adjustQuantity(quantity)
        
        if (adjustedQuantity <= 0.0) {
            AppLogger.logger.d { 
                "TradingEngine: Adjusted quantity too small for ${snapshot.symbol}" 
            }
            return null
        }
        
        // Step 8: Execute order
        AppLogger.logger.i { 
            "TradingEngine: Executing ${signal} order for ${snapshot.symbol}: " +
            "quantity=$adjustedQuantity, size=$positionSize, type=$orderType" 
        }
        
        val result = orderExecutor.execute(
            signal,
            adjustedQuantity,
            orderType,
            limitPrice
        )
        
        // Step 9: Record result in risk manager
        when (result) {
            is ExecutionResult.Success -> {
                // Calculate P&L (will be updated when position closes)
                // For now, just record as neutral
                AppLogger.logger.i { 
                    "TradingEngine: Order executed successfully: ${result.orderId}" 
                }
            }
            is ExecutionResult.Rejected -> {
                AppLogger.logger.w { 
                    "TradingEngine: Order rejected: ${result.reason}" 
                }
            }
            is ExecutionResult.Error -> {
                AppLogger.logger.e(throwable = result.throwable) { 
                    "TradingEngine: Order execution error: ${result.message}" 
                }
            }
            else -> {}
        }
        
        return result
    }
    
    /**
     * Evaluates strategy and generates trading signal
     * 
     * WHY: Combines imbalance and trade flow analysis
     */
    private fun evaluateStrategy(snapshot: MarketSnapshot): TradeSignal {
        // Calculate imbalance
        val imbalance = imbalanceCalculator.calculateImbalance(snapshot)
        
        // Check if imbalance suggests LONG
        if (imbalanceCalculator.suggestsLong(imbalance)) {
            // Confirm with trade flow
            if (tradeFlowAnalyzer.confirmsLong(snapshot.tradeFlow) &&
                tradeFlowAnalyzer.hasSufficientSamples(snapshot.tradeFlow)) {
                
                val confidence = imbalanceCalculator.calculateConfidence(imbalance, isLong = true)
                val entryPrice = snapshot.bestAsk  // Use best ask for LONG entry
                
                return TradeSignal.Long(
                    confidence = confidence,
                    entryPrice = entryPrice,
                    stopLoss = entryPrice * 0.99,  // 1% stop loss
                    takeProfit = entryPrice * 1.02  // 2% take profit
                )
            }
        }
        
        // Check if imbalance suggests SHORT
        if (imbalanceCalculator.suggestsShort(imbalance)) {
            // Confirm with trade flow
            if (tradeFlowAnalyzer.confirmsShort(snapshot.tradeFlow) &&
                tradeFlowAnalyzer.hasSufficientSamples(snapshot.tradeFlow)) {
                
                val confidence = imbalanceCalculator.calculateConfidence(imbalance, isLong = false)
                val entryPrice = snapshot.bestBid  // Use best bid for SHORT entry
                
                return TradeSignal.Short(
                    confidence = confidence,
                    entryPrice = entryPrice,
                    stopLoss = entryPrice * 1.01,  // 1% stop loss
                    takeProfit = entryPrice * 0.98  // 2% take profit
                )
            }
        }
        
        return TradeSignal.None
    }
    
    /**
     * Estimates position size for initial spread filter check
     * 
     * WHY: Need rough estimate before calculating exact size
     */
    private fun estimatePositionSize(snapshot: MarketSnapshot): Double {
        // Use conservative estimate: 0.5% of equity
        return equity * 0.005
    }
    
    /**
     * Calculates momentum indicator from trade flow
     * 
     * WHY: Used to determine if we should use market vs limit orders
     */
    private fun calculateMomentum(snapshot: MarketSnapshot): Double {
        // Simple momentum: ratio of aggressive volume to total volume
        val totalAggressive = snapshot.tradeFlow.aggressiveBuyVolume + 
                             snapshot.tradeFlow.aggressiveSellVolume
        return if (snapshot.tradeFlow.totalVolume > 0.0) {
            totalAggressive / snapshot.tradeFlow.totalVolume
        } else {
            1.0
        }
    }
    
    /**
     * Gets current risk status
     * 
     * WHY: Allows UI to display risk metrics
     */
    fun getRiskStatus(snapshot: MarketSnapshot): trading.risk.RiskStatus {
        return riskManager.getRiskStatus()
    }
}

