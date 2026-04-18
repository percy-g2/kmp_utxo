package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logging.AppLogger
import model.NewsItem
import model.OrderBookData
import model.RssProvider
import model.Ticker24hr
import network.NewsService
import network.OrderBookWebSocketService
import network.TickerWebSocketService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.ExperimentalTime
import network.HttpClient as NetworkClient

@OptIn(ExperimentalTime::class)
data class CoinDetailState(
    val isLoadingTicker: Boolean = false,
    val isLoadingNews: Boolean = false,
    val loadingNewsProviders: Set<String> = emptySet(),
    val news: List<NewsItem> = emptyList(),
    val ticker: Ticker24hr? = null,
    val orderBookData: OrderBookData? = null,
    val orderBookError: String? = null,
    val error: String? = null,
    val selectedTimeframe: String = "1m"
)

@OptIn(ExperimentalTime::class)
class CoinDetailViewModel : ViewModel() {
    private val httpClient = NetworkClient()
    private val newsService = NewsService()
    private val orderBookService = OrderBookWebSocketService()
    private val tickerWebSocketService = TickerWebSocketService()

    val state: StateFlow<CoinDetailState>
        field = MutableStateFlow(CoinDetailState())

    private var currentLoadJob: Job? = null
    private var timeframeChangeJob: Job? = null
    private var currentSymbol: String? = null

    private val newsUpdateMutex = Mutex()

    init {
        viewModelScope.launch {
            orderBookService.orderBookData.collect { orderBook ->
                state.update {
                    it.copy(
                        orderBookData = orderBook,
                        orderBookError = if (orderBook != null) null else it.orderBookError
                    )
                }
            }
        }

        viewModelScope.launch {
            orderBookService.error.collect { error ->
                state.update { it.copy(orderBookError = error) }
            }
        }

        viewModelScope.launch {
            tickerWebSocketService.tickerData.collect { ticker ->
                if (ticker != null) {
                    state.update { it.copy(ticker = ticker) }
                }
            }
        }
    }

    private suspend fun updateNewsState(update: (CoinDetailState) -> CoinDetailState) {
        newsUpdateMutex.withLock {
            state.value = update(state.value)
        }
    }

    fun loadCoinData(symbol: String, enabledRssProviders: Set<String> = RssProvider.DEFAULT_ENABLED_PROVIDERS) {
        currentLoadJob?.cancel()

        currentSymbol = symbol
        val timeframe = state.value.selectedTimeframe

        orderBookService.connect(symbol, levels = 20)
        tickerWebSocketService.connect(symbol)

        currentLoadJob = viewModelScope.launch {
            AppLogger.logger.d { "CoinDetailViewModel: Starting loadCoinData for $symbol with providers: $enabledRssProviders, timeframe: $timeframe" }

            state.value = CoinDetailState(
                isLoadingTicker = true,
                isLoadingNews = enabledRssProviders.isNotEmpty(),
                loadingNewsProviders = enabledRssProviders.toSet(),
                news = emptyList(),
                ticker = null,
                orderBookData = null,
                orderBookError = null,
                error = null,
                selectedTimeframe = timeframe
            )

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

            if (enabledRssProviders.isNotEmpty()) {
                launch {
                    try {
                        val providersToLoad = enabledRssProviders.toSet()

                        val providerJobs = RssProvider.ALL_PROVIDERS
                            .filter { providersToLoad.contains(it.id) }
                            .map { provider ->
                                async {
                                    try {
                                        val news = newsService.fetchNewsFromProvider(provider, symbol)

                                        val isFailure = news.isEmpty()

                                        updateNewsState { currentState ->
                                            val updatedNews = if (news.isNotEmpty()) {
                                                (currentState.news + news)
                                                    .distinctBy { it.link }
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
                                                loadingNewsProviders = currentState.loadingNewsProviders - provider.id
                                            )
                                        }

                                        if (isFailure) {
                                            AppLogger.logger.w { "CoinDetailViewModel: Provider ${provider.name} returned no news items" }
                                        }

                                        news
                                    } catch (e: Exception) {
                                        AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading news from ${provider.name}" }
                                        updateNewsState { currentState ->
                                            currentState.copy(
                                                loadingNewsProviders = currentState.loadingNewsProviders - provider.id
                                            )
                                        }
                                        emptyList()
                                    }
                                }
                            }

                        providerJobs.awaitAll()

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

    fun changeTimeframe(timeframe: String) {
        timeframeChangeJob?.cancel()
        timeframeChangeJob = viewModelScope.launch {
            delay(200)
            if (state.value.selectedTimeframe == timeframe) return@launch
            AppLogger.logger.d { "CoinDetailViewModel: Changing timeframe to $timeframe" }
            state.update { it.copy(selectedTimeframe = timeframe) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timeframeChangeJob?.cancel()
        currentLoadJob?.cancel()
        orderBookService.close()
        tickerWebSocketService.close()
        httpClient.close()
        newsService.close()
    }
}
