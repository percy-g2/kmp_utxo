package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import network.AiInsightService
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
    val selectedTimeframe: String = "1m",
    val isLoadingInsight: Boolean = false,
    val aiInsight: String? = null,
    val insightError: String? = null,
    val insightNeedsKey: Boolean = false
)

@OptIn(ExperimentalTime::class)
class CoinDetailViewModel : ViewModel() {
    private val httpClient = NetworkClient()
    private val newsService = NewsService()
    private val orderBookService = OrderBookWebSocketService()
    private val tickerWebSocketService = TickerWebSocketService()
    private val aiInsightService = AiInsightService()

    val state: StateFlow<CoinDetailState>
        field = MutableStateFlow(CoinDetailState())

    private var currentLoadJob: Job? = null
    private var timeframeChangeJob: Job? = null
    private var insightJob: Job? = null
    private var currentSymbol: String? = null
    private var currentApiKey: String = ""

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

    fun loadCoinData(
        symbol: String,
        enabledRssProviders: Set<String> = RssProvider.DEFAULT_ENABLED_PROVIDERS,
        aiApiKey: String = ""
    ) {
        currentLoadJob?.cancel()
        insightJob?.cancel()

        currentSymbol = symbol
        currentApiKey = aiApiKey
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
                selectedTimeframe = timeframe,
                // Keep the insight card in its loading state until both the ticker and the
                // news have been gathered and the combined insight has been generated.
                isLoadingInsight = true
            )

            // Fetch the ticker and news concurrently, then generate a single insight that
            // combines the 24h market data with the coin's recent news.
            val tickerDeferred = async { loadTicker(symbol) }
            val newsDeferred = async { loadNews(symbol, enabledRssProviders) }

            val ticker = tickerDeferred.await()
            val news = newsDeferred.await()

            if (ticker != null) {
                generateInsightFor(symbol, ticker, news, currentApiKey)
            } else {
                state.update { it.copy(isLoadingInsight = false) }
            }
        }
    }

    /** Fetches the 24h ticker, updates state, and returns it (or null on failure). */
    private suspend fun loadTicker(symbol: String): Ticker24hr? {
        return try {
            val ticker = httpClient.fetchTicker24hr(symbol)
            state.update { it.copy(ticker = ticker, isLoadingTicker = false) }
            if (ticker == null) {
                AppLogger.logger.w { "CoinDetailViewModel: Failed to fetch ticker for symbol: $symbol" }
            }
            ticker
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading ticker for $symbol" }
            state.update { it.copy(isLoadingTicker = false) }
            null
        }
    }

    /**
     * Loads news from every enabled provider in parallel, updating state incrementally as
     * each provider returns, and returns the final combined/deduped news list.
     */
    private suspend fun loadNews(symbol: String, enabledRssProviders: Set<String>): List<NewsItem> {
        if (enabledRssProviders.isEmpty()) {
            state.update { it.copy(isLoadingNews = false, loadingNewsProviders = emptySet()) }
            return emptyList()
        }
        return try {
            val providersToLoad = enabledRssProviders.toSet()

            coroutineScope {
                RssProvider.ALL_PROVIDERS
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
                    .awaitAll()
            }

            state.update { it.copy(isLoadingNews = false) }

            AppLogger.logger.i { "CoinDetailViewModel: Finished loading news - ${state.value.news.size} items" }

            state.value.news
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
            emptyList()
        }
    }

    fun refresh(
        symbol: String,
        enabledRssProviders: Set<String> = RssProvider.DEFAULT_ENABLED_PROVIDERS,
        aiApiKey: String = currentApiKey
    ) {
        viewModelScope.launch {
            newsService.clearCache()
            loadCoinData(symbol, enabledRssProviders, aiApiKey)
        }
    }

    /**
     * Regenerates the AI insight for the current coin using the already-fetched ticker and
     * the news currently shown on screen.
     */
    fun regenerateInsight() {
        val symbol = currentSymbol ?: return
        val ticker = state.value.ticker ?: return
        generateInsightFor(symbol, ticker, state.value.news, currentApiKey)
    }

    private fun generateInsightFor(symbol: String, ticker: Ticker24hr, news: List<NewsItem>, apiKey: String) {
        insightJob?.cancel()
        insightJob = viewModelScope.launch {
            state.update {
                it.copy(isLoadingInsight = true, insightError = null, insightNeedsKey = false)
            }
            val baseAsset = extractBaseAsset(symbol)
            when (val result = aiInsightService.generateInsight(symbol, baseAsset, ticker, news, apiKey)) {
                is AiInsightService.InsightResult.Success -> state.update {
                    it.copy(
                        aiInsight = result.text,
                        isLoadingInsight = false,
                        insightError = null,
                        insightNeedsKey = false
                    )
                }

                is AiInsightService.InsightResult.AuthRequired -> state.update {
                    it.copy(isLoadingInsight = false, insightNeedsKey = true, aiInsight = null)
                }

                is AiInsightService.InsightResult.Failure -> state.update {
                    it.copy(isLoadingInsight = false, insightError = result.message)
                }
            }
        }
    }

    /** Strips a common quote-currency suffix so "BTCUSDT" -> "BTC" for the prompt. */
    private fun extractBaseAsset(symbol: String): String {
        val quoteCurrencies = listOf(
            "FDUSD", "BUSD", "TUSD", "USDT", "USDC", "USD1", "DAI",
            "BTC", "ETH", "BNB", "EUR", "GBP", "JPY"
        ).sortedByDescending { it.length }
        val upper = symbol.uppercase()
        for (quote in quoteCurrencies) {
            if (upper.endsWith(quote) && upper.length > quote.length) {
                return upper.removeSuffix(quote)
            }
        }
        return upper
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
        insightJob?.cancel()
        orderBookService.close()
        tickerWebSocketService.close()
        httpClient.close()
        newsService.close()
        aiInsightService.close()
    }
}
