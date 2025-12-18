package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logging.AppLogger
import model.NewsItem
import model.OrderBookData
import model.RssProvider
import model.Ticker24hr
import model.UiKline
import network.NewsService
import network.OrderBookWebSocketService
import network.TickerWebSocketService
import trading.api.AggTradeWebSocketService
import trading.config.StrategyConfig
import trading.data.AggTrade
import trading.data.MarketSnapshotBuilder
import trading.data.TradeFlowMetrics
import trading.engine.TradingEngine
import trading.engine.TradingEngineFactory
import trading.execution.MockOrderExecutor
import trading.risk.RiskStatus
import trading.strategy.ImbalanceCalculator
import trading.strategy.TradeFlowAnalyzer
import trading.strategy.TradeSignal
import trading.data.OrderBook as TradingOrderBook
import io.ktor.client.HttpClient as KtorHttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import network.HttpClient as NetworkClient

@OptIn(ExperimentalTime::class)
data class CoinDetailState(
    val isLoadingChart: Boolean = false,
    val isLoadingTicker: Boolean = false,
    val isLoadingNews: Boolean = false,
    val loadingNewsProviders: Set<String> = emptySet(), // Track which RSS providers are still loading
    val news: List<NewsItem> = emptyList(),
    val ticker: Ticker24hr? = null,
    val klines: List<UiKline> = emptyList(),
    val orderBookData: OrderBookData? = null,
    val orderBookError: String? = null,
    val error: String? = null,
    // Trading engine state
    val tradingEnabled: Boolean = false,
    val currentSignal: TradeSignal = TradeSignal.None,
    val orderBookImbalance: Double = 1.0,
    val tradeFlowMetrics: TradeFlowMetrics? = null,
    val riskStatus: RiskStatus? = null,
    val lastTradeResult: String? = null
)

@OptIn(ExperimentalTime::class)
class CoinDetailViewModel : ViewModel() {
    companion object {
        private const val MAX_KLINES = 200
    }
    
    private val httpClient = NetworkClient()
    private val newsService = NewsService()
    private val orderBookService = OrderBookWebSocketService()
    private val tickerWebSocketService = TickerWebSocketService()
    private val aggTradeService = AggTradeWebSocketService()
    
    // Trading engine components
    private val tradingConfig = StrategyConfig.DEFAULT
    private val tradingEquity = 10000.0  // $10,000 default equity
    // Create separate HTTP client for trading engine (requires io.ktor.client.HttpClient)
    private val tradingHttpClient = KtorHttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    private val tradingEngineFactory = TradingEngineFactory(
        httpClient = tradingHttpClient,
        config = tradingConfig,
        equity = tradingEquity,
        orderExecutor = MockOrderExecutor()  // Use mock for safety
    )
    private val tradingEngine: TradingEngine = tradingEngineFactory.create()
    private val imbalanceCalculator = ImbalanceCalculator(
        longThreshold = tradingConfig.imbalanceLong,
        shortThreshold = tradingConfig.imbalanceShort,
        topNLevels = tradingConfig.topNLevels
    )
    private val tradeFlowAnalyzer = TradeFlowAnalyzer(
        confirmationThreshold = tradingConfig.tradeFlowThreshold
    )
    private val snapshotBuilder = MarketSnapshotBuilder(tradeFlowAnalyzer)
    
    val state: StateFlow<CoinDetailState>
        field = MutableStateFlow(CoinDetailState())
    
    // Track the current loading job to cancel it when a new one starts
    private var currentLoadJob: kotlinx.coroutines.Job? = null
    
    // Mutex to synchronize news state updates
    private val newsUpdateMutex = Mutex()
    
    // Track tooltip visibility and queue updates
    private var isTooltipVisible = false
    private val queuedKlineUpdates = mutableListOf<UiKline>()
    private val klineUpdateMutex = Mutex()
    
    init {
        // Observe order book updates
        viewModelScope.launch {
            orderBookService.orderBookData.collect { orderBook ->
                // Clear error when order book data is successfully received
                state.update { 
                    it.copy(
                        orderBookData = orderBook,
                        orderBookError = if (orderBook != null) null else it.orderBookError
                    )
                }
            }
        }
        
        // Observe order book errors
        viewModelScope.launch {
            orderBookService.error.collect { error ->
                state.update { it.copy(orderBookError = error) }
            }
        }
        
        // Observe aggregated trades for trading engine
        viewModelScope.launch {
            aggTradeService.trades.collect { trades ->
                // Update trading metrics when we have order book and trades
                val currentState = state.value
                if (currentState.orderBookData != null && trades.isNotEmpty()) {
                    updateTradingMetrics(currentState.orderBookData!!, trades)
                }
            }
        }
        
        // Process trading signals when order book updates
        viewModelScope.launch {
            orderBookService.orderBookData.collect { orderBook ->
                if (orderBook != null && state.value.tradingEnabled) {
                    val trades = aggTradeService.trades.value.take(500)
                    if (trades.isNotEmpty()) {
                        processTradingUpdate(orderBook, trades)
                    }
                }
            }
        }
        
        // Observe ticker WebSocket updates for real-time price data
        viewModelScope.launch {
            tickerWebSocketService.tickerData.collect { ticker ->
                if (ticker != null) {
                    val currentState = state.value
                    
                    // Always update ticker immediately
                    // Only update klines if initial data is loaded
                    if (currentState.klines.isNotEmpty() && !currentState.isLoadingChart) {
                        val currentTimestamp = Clock.System.now().toEpochMilliseconds()
                        val newKline = UiKline(
                            closePrice = ticker.lastPrice,
                            closeTime = currentTimestamp,
                            openTime = currentTimestamp
                        )
                        
                        klineUpdateMutex.withLock {
                            if (isTooltipVisible) {
                                // Queue the update when tooltip is visible
                                queuedKlineUpdates.add(newKline)
                                // Only update ticker, keep klines unchanged
                                state.update { it.copy(ticker = ticker) }
                            } else {
                                // Apply immediately when tooltip is not visible
                                state.update { currentState ->
                                    val currentKlines = currentState.klines
                                    val newKlinesList = if (currentKlines.size >= MAX_KLINES) {
                                        currentKlines.drop(1) + newKline
                                    } else {
                                        currentKlines + newKline
                                    }
                                    currentState.copy(
                                        ticker = ticker,
                                        klines = newKlinesList
                                    )
                                }
                            }
                        }
                    } else {
                        // Initial load or no klines yet - just update ticker
                        state.update { it.copy(ticker = ticker) }
                    }
                }
            }
        }
    }
    
    /**
     * Helper function to atomically update state within the mutex.
     * Ensures we always read the most recent state before applying updates,
     * preventing race conditions where concurrent updates overwrite each other.
     */
    private suspend fun updateNewsState(update: (CoinDetailState) -> CoinDetailState) {
        newsUpdateMutex.withLock {
            state.value = update(state.value)
        }
    }

    fun loadCoinData(symbol: String, enabledRssProviders: Set<String> = RssProvider.DEFAULT_ENABLED_PROVIDERS) {
        // Cancel any existing load job
        currentLoadJob?.cancel()
        
        // Connect to WebSocket streams for real-time data
        orderBookService.connect(symbol, levels = 20)
        tickerWebSocketService.connect(symbol)
        
        currentLoadJob = viewModelScope.launch {
            AppLogger.logger.d { "CoinDetailViewModel: Starting loadCoinData for $symbol with providers: $enabledRssProviders" }
            
            // Reset state and set loading flags
            state.value = CoinDetailState(
                isLoadingChart = true,
                isLoadingTicker = true,
                isLoadingNews = enabledRssProviders.isNotEmpty(),
                loadingNewsProviders = enabledRssProviders.toSet(),
                news = emptyList(),
                ticker = null,
                klines = emptyList(),
                orderBookData = null,
                orderBookError = null,
                error = null
            )
            
            // Load chart data independently
            launch {
                try {
                    val klinesResult = httpClient.fetchUiKlines(listOf(symbol))
                    val klines = klinesResult[symbol] ?: emptyList()
                    state.update {
                        it.copy(
                            klines = klines,
                            isLoadingChart = false
                        )
                    }
                    if (klines.isEmpty()) {
                        AppLogger.logger.d { "CoinDetailViewModel: No kline data found for symbol: $symbol" }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading klines for $symbol" }
                    state.update { it.copy(isLoadingChart = false) }
                }
            }
            
            // Load ticker data independently
            launch {
                try {
                    val ticker = httpClient.fetchTicker24hr(symbol)
                    state.update {
                        it.copy(
                            ticker = ticker,
                            isLoadingTicker = false
                        )
                    }
                    if (ticker == null) {
                        AppLogger.logger.w { "CoinDetailViewModel: Failed to fetch ticker for symbol: $symbol" }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading ticker for $symbol" }
                    state.update { it.copy(isLoadingTicker = false) }
                }
            }
            
            // Load news data independently from each RSS provider
            if (enabledRssProviders.isNotEmpty()) {
                launch {
                    try {
                        val providersToLoad = enabledRssProviders.toSet()
                        
                        // Fetch from each provider independently
                        val providerJobs = RssProvider.ALL_PROVIDERS
                            .filter { providersToLoad.contains(it.id) }
                            .map { provider ->
                                async {
                                    try {
                                        val news = newsService.fetchNewsFromProvider(provider, symbol)
                                        
                                        // If provider failed (returned empty list), remove from loading immediately
                                        // This prevents showing shimmer for failed providers
                                        val isFailure = news.isEmpty()
                                        
                                        // Update state atomically as each provider responds
                                        // Use helper function to ensure we always read the most recent state
                                        updateNewsState { currentState ->
                                            val updatedNews = if (news.isNotEmpty()) {
                                                (currentState.news + news)
                                                    .distinctBy { it.link } // Remove duplicates
                                                    .sortedByDescending { item ->
                                                        try {
                                                            ktx.parseRssDate(item.pubDate)
                                                        } catch (_: Exception) {
                                                            kotlin.time.Instant.fromEpochMilliseconds(0)
                                                        }
                                                    }
                                                    .take(50)
                                            } else {
                                                currentState.news
                                            }
                                            
                                            currentState.copy(
                                                news = updatedNews,
                                                // Remove provider from loading set immediately if it failed or succeeded
                                                loadingNewsProviders = currentState.loadingNewsProviders - provider.id
                                            )
                                        }
                                        
                                        if (isFailure) {
                                            AppLogger.logger.w { "CoinDetailViewModel: Provider ${provider.name} returned no news items" }
                                        }
                                        
                                        news
                                    } catch (e: Exception) {
                                        AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading news from ${provider.name}" }
                                        // Use helper function to ensure we always read the most recent state
                                        // Remove failed provider immediately to prevent shimmer
                                        updateNewsState { currentState ->
                                            currentState.copy(
                                                loadingNewsProviders = currentState.loadingNewsProviders - provider.id
                                            )
                                        }
                                        emptyList()
                                    }
                                }
                            }
                        
                        // Wait for all providers to complete
                        providerJobs.awaitAll()
                        
                        // Mark news loading as complete
                        state.update { it.copy(isLoadingNews = false) }
                        
                        AppLogger.logger.i { "CoinDetailViewModel: Finished loading news - ${state.value.news.size} items" }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading news for $symbol" }
                        state.update {
                            it.copy(
                                isLoadingNews = false,
                                loadingNewsProviders = emptySet()
                            )
                        }
                    }
                }
            } else {
                // No providers enabled, mark news as not loading
                state.update { it.copy(isLoadingNews = false, loadingNewsProviders = emptySet()) }
            }
        }
    }

    fun refresh(symbol: String, enabledRssProviders: Set<String> = RssProvider.DEFAULT_ENABLED_PROVIDERS) {
        viewModelScope.launch {
            newsService.clearCache()
            loadCoinData(symbol, enabledRssProviders)
        }
    }
    
    fun clearCache() {
        viewModelScope.launch {
            newsService.clearCache()
        }
    }
    
    /**
     * Enable or disable trading engine
     */
    fun setTradingEnabled(enabled: Boolean, symbol: String) {
        viewModelScope.launch {
            state.update { it.copy(tradingEnabled = enabled) }
            if (enabled) {
                aggTradeService.connect(symbol)
            } else {
                aggTradeService.disconnect()
                state.update { 
                    it.copy(
                        currentSignal = TradeSignal.None,
                        orderBookImbalance = 1.0,
                        tradeFlowMetrics = null
                    )
                }
            }
        }
    }
    
    /**
     * Update trading metrics from order book and trades
     */
    private suspend fun updateTradingMetrics(orderBook: OrderBookData, trades: List<AggTrade>) {
        val imbalance = TradingOrderBook.calculateImbalance(orderBook, tradingConfig.topNLevels)
        val tradeFlow = tradeFlowAnalyzer.calculateMetrics(trades, tradingConfig.tradeFlowWindowMs)
        
        // Get risk status if we have a snapshot
        val snapshot = snapshotBuilder.build(
            symbol = orderBook.symbol,
            orderBook = orderBook,
            trades = trades
        )
        val riskStatus = tradingEngine.getRiskStatus(snapshot)
        
        state.update {
            it.copy(
                orderBookImbalance = imbalance,
                tradeFlowMetrics = tradeFlow,
                riskStatus = riskStatus
            )
        }
    }
    
    /**
     * Process trading update and generate signals
     */
    private suspend fun processTradingUpdate(orderBook: OrderBookData, trades: List<AggTrade>) {
        try {
            val snapshot = snapshotBuilder.build(
                symbol = orderBook.symbol,
                orderBook = orderBook,
                trades = trades
            )
            
            // Calculate current signal
            val imbalance = imbalanceCalculator.calculateImbalance(snapshot)
            val signalDirection = imbalanceCalculator.evaluate(snapshot)
            
            val signal = when {
                signalDirection > 0 && tradeFlowAnalyzer.confirmsLong(snapshot.tradeFlow) -> {
                    val confidence = imbalanceCalculator.calculateConfidence(imbalance, isLong = true)
                    TradeSignal.Long(
                        confidence = confidence,
                        entryPrice = snapshot.bestAsk,
                        stopLoss = snapshot.bestAsk * 0.99,
                        takeProfit = snapshot.bestAsk * 1.02
                    )
                }
                signalDirection < 0 && tradeFlowAnalyzer.confirmsShort(snapshot.tradeFlow) -> {
                    val confidence = imbalanceCalculator.calculateConfidence(imbalance, isLong = false)
                    TradeSignal.Short(
                        confidence = confidence,
                        entryPrice = snapshot.bestBid,
                        stopLoss = snapshot.bestBid * 1.01,
                        takeProfit = snapshot.bestBid * 0.98
                    )
                }
                else -> TradeSignal.None
            }
            
            state.update { it.copy(currentSignal = signal) }
            
            // Process through trading engine (will be logged but not executed with mock executor)
            val result = tradingEngine.onMarketUpdate(snapshot)
            val resultMessage = when (result) {
                is trading.execution.ExecutionResult.Success -> "Trade executed: ${result.orderId}"
                is trading.execution.ExecutionResult.Rejected -> "Trade rejected: ${result.reason}"
                is trading.execution.ExecutionResult.Error -> "Error: ${result.message}"
                else -> null
            }
            
            if (resultMessage != null) {
                state.update { it.copy(lastTradeResult = resultMessage) }
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "Error processing trading update" }
        }
    }
    
    /**
     * Set tooltip visibility state
     * When tooltip is shown, chart updates are queued
     * When tooltip is hidden, queued updates are applied
     */
    fun setTooltipVisible(visible: Boolean) {
        viewModelScope.launch {
            klineUpdateMutex.withLock {
                val wasVisible = isTooltipVisible
                isTooltipVisible = visible
                
                if (wasVisible && !visible && queuedKlineUpdates.isNotEmpty()) {
                    // Apply queued updates when tooltip is hidden
                    val currentState = state.value
                    val currentKlines = currentState.klines
                    val queueSize = queuedKlineUpdates.size
                    
                    // Apply all queued updates
                    var updatedKlines = currentKlines
                    for (queuedKline in queuedKlineUpdates) {
                        if (updatedKlines.size >= MAX_KLINES) {
                            updatedKlines = updatedKlines.drop(1) + queuedKline
                        } else {
                            updatedKlines = updatedKlines + queuedKline
                        }
                    }
                    
                    // Clear queue and update state
                    queuedKlineUpdates.clear()
                    state.update { it.copy(klines = updatedKlines) }
                    
                    AppLogger.logger.d { "CoinDetailViewModel: Applied $queueSize queued kline updates" }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        orderBookService.close()
        tickerWebSocketService.close()
        aggTradeService.close()
        httpClient.close()
        tradingHttpClient.close()
        newsService.close()
    }
}