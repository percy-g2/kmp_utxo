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
import network.AiInsightCache
import network.AiInsightService
import network.CoinContextCache
import network.NewsFetchResult
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
    val isLoadingTicker: Boolean = false,
    val isLoadingNews: Boolean = false,
    val loadingNewsProviders: Set<String> = emptySet(),
    /** Providers whose feed could not be reached at all, as opposed to ones that simply had
     *  nothing about this coin. Drives the news error state in CoinDetailScreen. */
    val failedNewsProviders: Set<String> = emptySet(),
    val news: List<NewsItem> = emptyList(),
    val ticker: Ticker24hr? = null,
    val orderBookData: OrderBookData? = null,
    val orderBookError: String? = null,
    val error: String? = null,
    val selectedTimeframe: String = "1m",
    val isLoadingInsight: Boolean = false,
    val aiInsight: String? = null,
    val insightError: String? = null,
    val insightRateLimited: Boolean = false
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

    /** Optional llm7.io token; "" means anonymous, which still works. */
    private var currentApiToken: String = ""

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

    /**
     * @param forceInsight regenerates the overview instead of reusing a cached one. Set by
     *   [refresh] — a pull/tap to refresh is a deliberate "give me current data" gesture, and the
     *   AI card must not be the one element on the screen that ignores it. The screen's own
     *   `LaunchedEffect` leaves this false so that merely revisiting a coin costs no request.
     */
    fun loadCoinData(
        symbol: String,
        enabledRssProviders: Set<String> = RssProvider.DEFAULT_ENABLED_PROVIDERS,
        aiApiToken: String = "",
        forceInsight: Boolean = false
    ) {
        currentLoadJob?.cancel()
        insightJob?.cancel()

        currentSymbol = symbol
        currentApiToken = aiApiToken
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

            // Hand the gathered context to the chat screen, which can't reach this ViewModel (on
            // iOS 26 it is a separate ComposeUIViewController) and would otherwise re-hit every RSS
            // feed before it could answer the first question. Published even when the ticker is
            // null, so the news alone is still reusable.
            CoinContextCache.put(
                symbol = symbol,
                context = CoinContextCache.CoinContext(ticker = ticker, news = news, overview = null),
                nowMillis = Clock.System.now().toEpochMilliseconds()
            )

            if (ticker != null) {
                generateInsightFor(symbol, ticker, news, forceRefresh = forceInsight)
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
                                val result = newsService.fetchNewsFromProvider(provider, symbol)
                                val news = (result as? NewsFetchResult.Success)?.items.orEmpty()
                                val failed = result is NewsFetchResult.Failed

                                updateNewsState { currentState ->
                                    val updatedNews = if (news.isNotEmpty()) {
                                        (currentState.news + news)
                                            .distinctBy { it.link }
                                            // Tie-break on the link: sortedBy* is stable, so items
                                            // sharing a pubDate (and every item with an unparseable
                                            // one, which all collapse to epoch 0) would otherwise
                                            // keep whichever order their provider happened to
                                            // return in. That made both the AI prompt and
                                            // [AiInsightCache]'s key non-reproducible run to run.
                                            .sortedWith(
                                                compareByDescending<NewsItem> { item ->
                                                    try {
                                                        ktx.parseRssDate(item.pubDate)
                                                    } catch (_: Exception) {
                                                        kotlin.time.Instant.fromEpochMilliseconds(0)
                                                    }
                                                }.thenBy { it.link }
                                            )
                                            .take(50)
                                    } else {
                                        currentState.news
                                    }

                                    currentState.copy(
                                        news = updatedNews,
                                        loadingNewsProviders = currentState.loadingNewsProviders - provider.id,
                                        failedNewsProviders = if (failed) {
                                            currentState.failedNewsProviders + provider.id
                                        } else {
                                            currentState.failedNewsProviders
                                        }
                                    )
                                }

                                if (failed) {
                                    AppLogger.logger.w { "CoinDetailViewModel: Provider ${provider.name} could not be reached" }
                                } else if (news.isEmpty()) {
                                    AppLogger.logger.d { "CoinDetailViewModel: Provider ${provider.name} had nothing matching $symbol" }
                                }

                                news
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                // Throwable, not Exception — Ktor's JS/Wasm engine reports a failed
                                // fetch as kotlin.Error, which would otherwise tear down this whole
                                // load job and leave the shimmers spinning forever.
                                AppLogger.logger.e(throwable = e) { "CoinDetailViewModel: Error loading news from ${provider.name}" }
                                updateNewsState { currentState ->
                                    currentState.copy(
                                        loadingNewsProviders = currentState.loadingNewsProviders - provider.id,
                                        failedNewsProviders = currentState.failedNewsProviders + provider.id
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
        } catch (e: Throwable) {
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
        aiApiToken: String = currentApiToken
    ) {
        viewModelScope.launch {
            loadCoinData(symbol, enabledRssProviders, aiApiToken, forceInsight = true)
        }
    }

    /**
     * Regenerates the AI insight for the current coin using the already-fetched ticker and
     * the news currently shown on screen.
     */
    fun regenerateInsight() {
        val symbol = currentSymbol ?: return
        val ticker = state.value.ticker ?: return
        // The user explicitly asked for a new overview, so don't hand back the cached one.
        generateInsightFor(symbol, ticker, state.value.news, forceRefresh = true)
    }

    /**
     * Adopts a newly saved llm7.io token without re-fetching the ticker or news.
     *
     * On the iOS 26 native tab bar this screen stays composed while Settings is edited, so the token
     * can change under a live screen. Without this the cached [currentApiToken] would keep the old
     * value and [regenerateInsight] (the card's Retry button) would keep sending it.
     *
     * Only regenerates when a ticker is already on screen; an in-flight [loadCoinData] needs no help
     * because [generateInsightFor] reads [currentApiToken] at send time.
     */
    fun updateApiToken(aiApiToken: String) {
        if (aiApiToken == currentApiToken) return
        currentApiToken = aiApiToken
        val symbol = currentSymbol ?: return
        val ticker = state.value.ticker ?: return
        // Saving a token is usually a response to being throttled, so give them a real attempt
        // rather than the overview the anonymous tier already produced.
        generateInsightFor(symbol, ticker, state.value.news, forceRefresh = true)
    }

    /**
     * @param forceRefresh skips [AiInsightCache] on the way in. Set for every deliberate user
     *   action — Retry, Refresh, saving a token. Only the screen's own load-on-open leaves it
     *   false, so that revisiting a coin within the cache TTL costs no llm7.io request.
     */
    private fun generateInsightFor(
        symbol: String,
        ticker: Ticker24hr,
        news: List<NewsItem>,
        forceRefresh: Boolean = false
    ) {
        insightJob?.cancel()
        insightJob = viewModelScope.launch {
            val newsFingerprint = AiInsightService.newsFingerprint(news)

            if (!forceRefresh) {
                val cached = AiInsightCache.get(
                    symbol = symbol,
                    newsFingerprint = newsFingerprint,
                    nowMillis = Clock.System.now().toEpochMilliseconds()
                )
                if (cached != null) {
                    AppLogger.logger.d { "CoinDetailViewModel: reusing cached insight for $symbol" }
                    state.update {
                        it.copy(
                            aiInsight = cached,
                            isLoadingInsight = false,
                            insightError = null,
                            insightRateLimited = false
                        )
                    }
                    return@launch
                }
            }

            state.update {
                it.copy(
                    isLoadingInsight = true,
                    insightError = null,
                    insightRateLimited = false
                )
            }
            val baseAsset = AiInsightService.extractBaseAsset(symbol)
            when (
                val result =
                    aiInsightService.generateInsight(symbol, baseAsset, ticker, news, currentApiToken)
            ) {
                is AiInsightService.InsightResult.Success -> {
                    AiInsightCache.put(
                        symbol = symbol,
                        newsFingerprint = newsFingerprint,
                        text = result.text,
                        nowMillis = Clock.System.now().toEpochMilliseconds()
                    )
                    // The chat opens with the overview already in its context, so "explain that"
                    // refers to the same paragraph the user is reading on the card.
                    CoinContextCache.putOverview(symbol, result.text)
                    state.update {
                        it.copy(
                            aiInsight = result.text,
                            isLoadingInsight = false,
                            insightError = null,
                            insightRateLimited = false
                        )
                    }
                }

                // Keep any insight already on screen: unlike an auth failure this is routine at the
                // anonymous tier (10 requests/min), so a Retry that gets throttled must not wipe a
                // perfectly good overview the user was reading. The card shows the limit alongside.
                is AiInsightService.InsightResult.RateLimited -> state.update {
                    it.copy(isLoadingInsight = false, insightRateLimited = true)
                }

                is AiInsightService.InsightResult.Failure -> state.update {
                    it.copy(isLoadingInsight = false, insightError = result.message)
                }
            }
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
