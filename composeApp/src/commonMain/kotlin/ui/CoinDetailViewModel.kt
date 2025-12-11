package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.NewsItem
import model.Ticker24hr
import network.NewsService
import network.HttpClient as NetworkClient

data class CoinDetailState(
    val isLoading: Boolean = false,
    val news: List<NewsItem> = emptyList(),
    val ticker: Ticker24hr? = null,
    val error: String? = null
)

class CoinDetailViewModel : ViewModel() {
    private val httpClient = NetworkClient()
    private val newsService = NewsService()
    
    private val _state = MutableStateFlow(CoinDetailState())
    val state: StateFlow<CoinDetailState> = _state.asStateFlow()

    fun loadCoinData(symbol: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                // Fetch news and ticker data in parallel
                val newsResult = newsService.fetchNewsForCoin(symbol)
                val tickerResult = httpClient.fetchTicker24hr(symbol)
                
                val news = newsResult.getOrElse { 
                    emptyList() // Return empty list on error, don't fail completely
                }
                val ticker = tickerResult
                
                // Log for debugging
                if (ticker == null) {
                    println("CoinDetailViewModel: Failed to fetch ticker for symbol: $symbol")
                }
                if (news.isEmpty()) {
                    println("CoinDetailViewModel: No news found for symbol: $symbol")
                }
                
                _state.value = CoinDetailState(
                    isLoading = false,
                    news = news,
                    ticker = ticker,
                    error = if (news.isEmpty() && ticker == null) "No data available for $symbol" else null
                )
            } catch (e: Exception) {
                println("CoinDetailViewModel: Error loading data for $symbol: ${e.message}")
                e.printStackTrace()
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load data: ${e.message}"
                )
            }
        }
    }

    fun refresh(symbol: String) {
        viewModelScope.launch {
            newsService.clearCache()
            loadCoinData(symbol)
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
        newsService.close()
    }
}