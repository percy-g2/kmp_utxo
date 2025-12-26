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
import network.KlineWebSocketService
import network.NewsService
import network.OrderBookWebSocketService
import network.TickerWebSocketService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val selectedTimeframe: String = "1m" // Default timeframe
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
    private val klineWebSocketService = KlineWebSocketService()
    
    val state: StateFlow<CoinDetailState>
        field = MutableStateFlow(CoinDetailState())
    
    // Track the current loading job to cancel it when a new one starts
    private var currentLoadJob: kotlinx.coroutines.Job? = null
    
    // Track debounced timeframe change job
    private var timeframeChangeJob: Job? = null
    
    // Current symbol for timeframe changes
    private var currentSymbol: String? = null
    
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
        
        // Observe ticker WebSocket updates for real-time price data
        viewModelScope.launch {
            tickerWebSocketService.tickerData.collect { ticker ->
                if (ticker != null) {
                    // Only update ticker, klines are now handled by kline WebSocket
                    state.update { it.copy(ticker = ticker) }
                }
            }
        }
        
        // Observe kline WebSocket updates for real-time chart data
        viewModelScope.launch {
            klineWebSocketService.klineData.collect { kline ->
                if (kline != null) {
                    val currentState = state.value
                    
                    // Only update klines if initial data is loaded and not loading
                    if (currentState.klines.isNotEmpty() && !currentState.isLoadingChart) {
                        klineUpdateMutex.withLock {
                            if (isTooltipVisible) {
                                // Queue the update when tooltip is visible
                                queuedKlineUpdates.add(kline)
                            } else {
                                // Apply immediately when tooltip is not visible
                                state.update { currentState ->
                                    val currentKlines = currentState.klines
                                    // Update the last kline if it has the same openTime, otherwise append
                                    val newKlinesList = if (currentKlines.isNotEmpty() && 
                                        currentKlines.last().openTime == kline.openTime) {
                                        // Update existing kline
                                        currentKlines.dropLast(1) + kline
                                    } else {
                                        // Add new kline
                                        if (currentKlines.size >= MAX_KLINES) {
                                            currentKlines.drop(1) + kline
                                        } else {
                                            currentKlines + kline
                                        }
                                    }
                                    currentState.copy(klines = newKlinesList)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Observe kline WebSocket errors
        viewModelScope.launch {
            klineWebSocketService.error.collect { error ->
                if (error != null) {
                    AppLogger.logger.w { "KlineWebSocket error: $error" }
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
        
        currentSymbol = symbol
        val timeframe = state.value.selectedTimeframe
        
        // Connect to WebSocket streams for real-time data
        orderBookService.connect(symbol, levels = 20)
        tickerWebSocketService.connect(symbol)
        klineWebSocketService.connect(symbol, timeframe)
        
        currentLoadJob = viewModelScope.launch {
            AppLogger.logger.d { "CoinDetailViewModel: Starting loadCoinData for $symbol with providers: $enabledRssProviders, timeframe: $timeframe" }
            
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
                error = null,
                selectedTimeframe = timeframe
            )
            
            // Load chart data independently with selected timeframe
            launch {
                try {
                    val klines = httpClient.fetchKlines(symbol, timeframe, limit = 500)
                    state.update {
                        it.copy(
                            klines = klines,
                            isLoadingChart = false
                        )
                    }
                    if (klines.isEmpty()) {
                        AppLogger.logger.d { "CoinDetailViewModel: No kline data found for symbol: $symbol@$timeframe" }
                    } else {
                        AppLogger.logger.d { "CoinDetailViewModel: Loaded ${klines.size} klines for $symbol@$timeframe" }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading klines for $symbol@$timeframe" }
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
     * Change timeframe with debounce (500ms)
     * Cancels existing connection, fetches historical klines, and starts WebSocket
     */
    fun changeTimeframe(timeframe: String) {
        timeframeChangeJob?.cancel()
        timeframeChangeJob = viewModelScope.launch {
            delay(500) // Debounce 500ms
            
            val symbol = currentSymbol ?: return@launch
            val currentTimeframe = state.value.selectedTimeframe
            
            // Don't reconnect if timeframe hasn't changed
            if (currentTimeframe == timeframe) {
                return@launch
            }
            
            AppLogger.logger.d { "CoinDetailViewModel: Changing timeframe from $currentTimeframe to $timeframe for $symbol" }
            
            // Update state with new timeframe
            state.update { it.copy(selectedTimeframe = timeframe, isLoadingChart = true) }
            
            // Cancel existing kline WebSocket connection
            klineWebSocketService.disconnect()
            
            // Fetch historical klines with new timeframe
            try {
                val klines = httpClient.fetchKlines(symbol, timeframe, limit = 500)
                state.update {
                    it.copy(
                        klines = klines,
                        isLoadingChart = false
                    )
                }
                if (klines.isEmpty()) {
                    AppLogger.logger.d { "CoinDetailViewModel: No kline data found for symbol: $symbol@$timeframe" }
                } else {
                    AppLogger.logger.d { "CoinDetailViewModel: Loaded ${klines.size} klines for $symbol@$timeframe" }
                }
            } catch (e: Exception) {
                AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading klines for $symbol@$timeframe" }
                state.update { it.copy(isLoadingChart = false) }
            }
            
            // Start WebSocket connection with new timeframe
            klineWebSocketService.connect(symbol, timeframe)
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
        timeframeChangeJob?.cancel()
        orderBookService.close()
        tickerWebSocketService.close()
        klineWebSocketService.close()
        httpClient.close()
        newsService.close()
    }
}