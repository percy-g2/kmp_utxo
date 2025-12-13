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
import model.RssProvider
import model.Ticker24hr
import model.UiKline
import network.NewsService
import network.HttpClient as NetworkClient
import ktx.parseRssDate
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
data class CoinDetailState(
    val isLoadingChart: Boolean = false,
    val isLoadingTicker: Boolean = false,
    val isLoadingNews: Boolean = false,
    val loadingNewsProviders: Set<String> = emptySet(), // Track which RSS providers are still loading
    val news: List<NewsItem> = emptyList(),
    val ticker: Ticker24hr? = null,
    val klines: List<UiKline> = emptyList(),
    val error: String? = null
)

@OptIn(ExperimentalTime::class)
class CoinDetailViewModel : ViewModel() {
    private val httpClient = NetworkClient()
    private val newsService = NewsService()
    
    private val _state = MutableStateFlow(CoinDetailState())
    val state: StateFlow<CoinDetailState> = _state.asStateFlow()
    
    // Track the current loading job to cancel it when a new one starts
    private var currentLoadJob: kotlinx.coroutines.Job? = null
    
    // Mutex to synchronize news state updates
    private val newsUpdateMutex = Mutex()
    
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

    fun loadCoinData(symbol: String, enabledRssProviders: Set<String> = model.RssProvider.DEFAULT_ENABLED_PROVIDERS) {
        // Cancel any existing load job
        currentLoadJob?.cancel()
        
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
                        val allNews = mutableListOf<NewsItem>()
                        val providersToLoad = enabledRssProviders.toSet()
                        
                        // Fetch from each provider independently
                        val providerJobs = model.RssProvider.ALL_PROVIDERS
                            .filter { providersToLoad.contains(it.id) }
                            .map { provider ->
                                async {
                                    try {
                                        val news = newsService.fetchNewsFromProvider(provider, symbol)
                                        // Update state atomically as each provider responds
                                        // Use helper function to ensure we always read the most recent state
                                        updateNewsState { currentState ->
                                            val updatedNews = if (news.isNotEmpty()) {
                                                (currentState.news + news)
                                                    .distinctBy { it.link } // Remove duplicates
                                                    .sortedByDescending { item ->
                                                        try {
                                                            ktx.parseRssDate(item.pubDate)
                                                        } catch (e: Exception) {
                                                            kotlinx.datetime.Instant.fromEpochMilliseconds(0)
                                                        }
                                                    }
                                                    .take(50)
                                            } else {
                                                currentState.news
                                            }
                                            
                                            currentState.copy(
                                                news = updatedNews,
                                                loadingNewsProviders = currentState.loadingNewsProviders - provider.id
                                            )
                                        }
                                        news
                                    } catch (e: Exception) {
                                        AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading news from ${provider.name}" }
                                        // Use helper function to ensure we always read the most recent state
                                        updateNewsState { currentState ->
                                            currentState.copy(
                                                loadingNewsProviders = currentState.loadingNewsProviders - provider.id
                                            )
                                        }
                                        emptyList<NewsItem>()
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

    fun refresh(symbol: String, enabledRssProviders: Set<String> = model.RssProvider.DEFAULT_ENABLED_PROVIDERS) {
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

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
        newsService.close()
    }
}