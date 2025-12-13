package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val error: String? = null
)

@OptIn(ExperimentalTime::class)
class CoinDetailViewModel : ViewModel() {
    private val httpClient = NetworkClient()
    private val newsService = NewsService()
    private val orderBookService = OrderBookWebSocketService()
    private val tickerWebSocketService = TickerWebSocketService()
    
    private val _state = MutableStateFlow(CoinDetailState())
    val state: StateFlow<CoinDetailState> = _state.asStateFlow()
    
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
                _state.value = _state.value.copy(orderBookData = orderBook)
            }
        }
        
        // Observe ticker WebSocket updates for real-time price data
        viewModelScope.launch {
            tickerWebSocketService.tickerData.collect { ticker ->
                if (ticker != null) {
                    val currentState = _state.value
                    
                    // Always update ticker immediately
                    // Only update klines if initial data is loaded
                    if (currentState.klines.isNotEmpty() && !currentState.isLoadingChart) {
                        val MAX_KLINES = 200
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
                                _state.value = currentState.copy(ticker = ticker)
                            } else {
                                // Apply immediately when tooltip is not visible
                                val currentKlines = currentState.klines
                                val newKlinesList = if (currentKlines.size >= MAX_KLINES) {
                                    currentKlines.drop(1) + newKline
                                } else {
                                    currentKlines + newKline
                                }
                                _state.value = currentState.copy(
                                    ticker = ticker,
                                    klines = newKlinesList
                                )
                            }
                        }
                    } else {
                        // Initial load or no klines yet - just update ticker
                        _state.value = currentState.copy(ticker = ticker)
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
            _state.value = update(_state.value)
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
            _state.value = CoinDetailState(
                isLoadingChart = true,
                isLoadingTicker = true,
                isLoadingNews = enabledRssProviders.isNotEmpty(),
                loadingNewsProviders = enabledRssProviders.toSet(),
                news = emptyList(),
                ticker = null,
                klines = emptyList(),
                orderBookData = null,
                error = null
            )
            
            // Load chart data independently
            launch {
                try {
                    val klinesResult = httpClient.fetchUiKlines(listOf(symbol))
                    val klines = klinesResult[symbol] ?: emptyList()
                    _state.value = _state.value.copy(
                        klines = klines,
                        isLoadingChart = false
                    )
                    if (klines.isEmpty()) {
                        AppLogger.logger.d { "CoinDetailViewModel: No kline data found for symbol: $symbol" }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading klines for $symbol" }
                    _state.value = _state.value.copy(isLoadingChart = false)
                }
            }
            
            // Load ticker data independently
            launch {
                try {
                    val ticker = httpClient.fetchTicker24hr(symbol)
                    _state.value = _state.value.copy(
                        ticker = ticker,
                        isLoadingTicker = false
                    )
                    if (ticker == null) {
                        AppLogger.logger.w { "CoinDetailViewModel: Failed to fetch ticker for symbol: $symbol" }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading ticker for $symbol" }
                    _state.value = _state.value.copy(isLoadingTicker = false)
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
                        _state.value = _state.value.copy(isLoadingNews = false)
                        
                        AppLogger.logger.i { "CoinDetailViewModel: Finished loading news - ${_state.value.news.size} items" }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading news for $symbol" }
                        _state.value = _state.value.copy(
                            isLoadingNews = false,
                            loadingNewsProviders = emptySet()
                        )
                    }
                }
            } else {
                // No providers enabled, mark news as not loading
                _state.value = _state.value.copy(isLoadingNews = false, loadingNewsProviders = emptySet())
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
                    val currentState = _state.value
                    val currentKlines = currentState.klines
                    val MAX_KLINES = 200
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
                    _state.value = currentState.copy(klines = updatedKlines)
                    
                    AppLogger.logger.d { "CoinDetailViewModel: Applied $queueSize queued kline updates" }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        orderBookService.close()
        tickerWebSocketService.close()
        httpClient.close()
        newsService.close()
    }
}