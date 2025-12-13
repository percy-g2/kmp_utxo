package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logging.AppLogger
import model.NewsItem
import model.RssProvider
import model.Ticker24hr
import model.UiKline
import network.NewsService
import network.HttpClient as NetworkClient

data class CoinDetailState(
    val isLoading: Boolean = false,
    val news: List<NewsItem> = emptyList(),
    val ticker: Ticker24hr? = null,
    val klines: List<UiKline> = emptyList(),
    val error: String? = null
)

class CoinDetailViewModel : ViewModel() {
    private val httpClient = NetworkClient()
    private val newsService = NewsService()
    
    private val _state = MutableStateFlow(CoinDetailState())
    val state: StateFlow<CoinDetailState> = _state.asStateFlow()
    
    // Track the current loading job to cancel it when a new one starts
    private var currentLoadJob: kotlinx.coroutines.Job? = null

    fun loadCoinData(symbol: String, enabledRssProviders: Set<String> = model.RssProvider.DEFAULT_ENABLED_PROVIDERS) {
        // Cancel any existing load job
        currentLoadJob?.cancel()
        
        currentLoadJob = viewModelScope.launch {
            AppLogger.logger.d { "CoinDetailViewModel: Starting loadCoinData for $symbol with providers: $enabledRssProviders" }
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                // Fetch news, ticker, and kline data in parallel
                val newsResult = newsService.fetchNewsForCoin(symbol, enabledRssProviders)
                val tickerResult = httpClient.fetchTicker24hr(symbol)
                val klinesResult = httpClient.fetchUiKlines(listOf(symbol))
                
                val news = newsResult.getOrElse { 
                    emptyList() // Return empty list on error, don't fail completely
                }
                val ticker = tickerResult
                val klines = klinesResult[symbol] ?: emptyList()
                
                // Log for debugging
                if (ticker == null) {
                    AppLogger.logger.w { "CoinDetailViewModel: Failed to fetch ticker for symbol: $symbol" }
                }
                if (news.isEmpty()) {
                    AppLogger.logger.d { "CoinDetailViewModel: No news found for symbol: $symbol" }
                }
                if (klines.isEmpty()) {
                    AppLogger.logger.d { "CoinDetailViewModel: No kline data found for symbol: $symbol" }
                }
                
                AppLogger.logger.i { "CoinDetailViewModel: Successfully loaded data - news: ${news.size} items" }
                _state.value = CoinDetailState(
                    isLoading = false,
                    news = news,
                    ticker = ticker,
                    klines = klines,
                    error = if (news.isEmpty() && ticker == null) "No data available for $symbol" else null
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                AppLogger.logger.d { "CoinDetailViewModel: Load cancelled for $symbol" }
                throw e // Re-throw cancellation exceptions
            } catch (e: Exception) {
                AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading data for $symbol" }
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load data: ${e.message}"
                )
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