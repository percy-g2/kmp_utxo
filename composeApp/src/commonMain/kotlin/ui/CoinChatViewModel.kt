package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logging.AppLogger
import model.ChatRole
import model.CoinChatMessage
import model.NewsItem
import model.RssProvider
import model.Ticker24hr
import network.AiInsightService
import network.CoinChatService
import network.CoinChatStore
import network.CoinContextCache
import network.NewsFetchResult
import network.NewsService
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import network.HttpClient as NetworkClient

data class CoinChatState(
    val messages: List<CoinChatMessage> = emptyList(),
    val input: String = "",
    /** A question is in flight; the send button is disabled and a thinking bubble is shown. */
    val isAwaitingReply: Boolean = false,
    val error: String? = null,
    val rateLimited: Boolean = false,
    /** The short ranked list shown above the input once a conversation is under way. */
    val suggestions: List<ChatSuggestion> = emptyList(),
    /**
     * Every answerable question, grouped by category — browsed on an empty thread, where there is a
     * whole screen for it. Depends only on the coin's context, so it is computed once.
     */
    val catalog: List<SuggestionGroup> = emptyList(),
    /** Kept in state so a chip can name the actual move ("Why is BTC down 4.2% today?"). */
    val ticker: Ticker24hr? = null
)

/**
 * Drives the per-coin AI chat.
 *
 * Scoped to its nav entry, so it deliberately owns very little: the transcript lives in
 * [CoinChatStore] and the market context comes from [CoinContextCache], both process-wide. That
 * keeps backing out of the chat and returning cheap, and means the ViewModel can be recreated
 * freely — which the iOS 26 native navigation stack does on every push.
 *
 * Only one request is ever in flight ([CoinChatState.isAwaitingReply] gates the send button), which
 * doubles as the throttle against llm7.io's anonymous tier of 10 requests a minute.
 */
@OptIn(ExperimentalTime::class)
class CoinChatViewModel : ViewModel() {
    private val httpClient = NetworkClient()
    private val newsService = NewsService()
    private val chatService = CoinChatService()

    val state: StateFlow<CoinChatState>
        field = MutableStateFlow(CoinChatState())

    private var askJob: Job? = null
    private var startJob: Job? = null

    /**
     * Completes once the coin's market data and news are in hand. [dispatch] joins it, so a question
     * typed during the cold-path RSS sweep still reaches the model with the news attached rather
     * than being answered from an empty context the greeting promised was there.
     */
    private var contextJob: Job? = null

    private var symbol: String = ""
    private var baseAsset: String = ""
    private var news: List<NewsItem> = emptyList()
    private var overview: String? = null

    /** Optional llm7.io token; "" means anonymous, which still works. */
    private var apiToken: String = ""

    private var started = false

    /** When the 24h ticker was last read, so [refreshTicker] can skip a redundant round trip. */
    private var lastTickerFetchMillis = 0L

    /**
     * Restores the transcript, resolves the coin's market context, and optionally fires a question
     * the user already chose by tapping a chip on the coin screen.
     *
     * Idempotent: the screen calls this from a `LaunchedEffect` that can re-run, and re-resolving
     * the context would cost a redundant ticker fetch and, on a cache miss, a full RSS sweep.
     */
    fun start(
        symbol: String,
        aiApiToken: String,
        enabledRssProviders: Set<String> = RssProvider.DEFAULT_ENABLED_PROVIDERS,
        initialQuestion: String? = null
    ) {
        if (started) return
        started = true

        this.symbol = symbol
        this.baseAsset = AiInsightService.extractBaseAsset(symbol)
        this.apiToken = aiApiToken

        contextJob = viewModelScope.launch {
            val restored = CoinChatStore.get(symbol)

            // Offer the questions that need no context straight away. Resolving the coin's market
            // data and headlines can take tens of seconds on a cache miss — a whole RSS sweep — and
            // waiting for it left the screen as a greeting above an empty void for the whole time.
            // The market and news questions join the list below once there is something to answer
            // them from.
            state.update {
                it.copy(
                    messages = restored,
                    suggestions = suggestions(restored),
                    catalog = suggestionCatalog(
                        baseAsset = baseAsset,
                        ticker = null,
                        hasNews = false,
                        hasOverview = false
                    )
                )
            }

            resolveContext(symbol, enabledRssProviders)

            state.update {
                it.copy(
                    suggestions = suggestions(it.messages),
                    catalog = suggestionCatalog(
                        baseAsset = baseAsset,
                        ticker = it.ticker,
                        hasNews = news.isNotEmpty(),
                        hasOverview = !overview.isNullOrBlank()
                    )
                )
            }
        }

        // Separate from [contextJob] rather than tacked onto its end: [dispatch] joins that job, so
        // sending from inside it would wait on itself.
        startJob = viewModelScope.launch {
            val question = initialQuestion?.trim()
            if (!question.isNullOrBlank()) send(question)
        }
    }

    /**
     * Fills in ticker/news/overview from what the coin screen already gathered, falling back to
     * fetching them here.
     *
     * The fallback is the cold path — opening the chat without visiting the coin screen first, or
     * returning to it after the 10-minute TTL — and it is genuinely slow, because [NewsService]
     * re-requests every enabled RSS feed. Hence the cache.
     */
    private suspend fun resolveContext(symbol: String, enabledRssProviders: Set<String>) {
        val cached = CoinContextCache.get(symbol, Clock.System.now().toEpochMilliseconds())
        if (cached != null) {
            news = cached.news
            overview = cached.overview
            state.update { it.copy(ticker = cached.ticker) }
            AppLogger.logger.d { "CoinChatViewModel: reusing cached context for $symbol" }
            // A cached ticker can be minutes old; the answer should quote the current price.
            refreshTicker()
            return
        }

        AppLogger.logger.d { "CoinChatViewModel: no cached context for $symbol, fetching" }
        try {
            coroutineScope {
                val tickerDeferred = async { fetchTicker(symbol) }
                val newsDeferred = async { fetchNews(symbol, enabledRssProviders) }
                val ticker = tickerDeferred.await()
                news = newsDeferred.await()
                state.update { it.copy(ticker = ticker) }
            }
            CoinContextCache.put(
                symbol = symbol,
                context = CoinContextCache.CoinContext(state.value.ticker, news, overview),
                nowMillis = Clock.System.now().toEpochMilliseconds()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A chat with no market context still works — the prompt says to admit what it lacks.
            AppLogger.logger.e(throwable = e) { "CoinChatViewModel: failed to build context for $symbol" }
        }
    }

    fun updateInput(text: String) {
        state.update { it.copy(input = text) }
    }

    /** Adopts a newly saved llm7.io token; on the iOS 26 tab bar Settings can be edited under a live screen. */
    fun updateApiToken(aiApiToken: String) {
        apiToken = aiApiToken
    }

    fun send(text: String) {
        val question = text.trim()
        if (question.isBlank() || state.value.isAwaitingReply) return
        val messages = state.value.messages
        val userMessage = CoinChatMessage(
            id = messages.lastOrNull()?.id?.plus(1) ?: 0L,
            role = ChatRole.User,
            text = question.take(CoinChatService.MAX_QUESTION_CHARS)
        )
        state.update { it.copy(messages = it.messages + userMessage, input = "") }
        dispatch()
    }

    /**
     * Re-sends the question that failed. A failed turn leaves the user's bubble in place rather
     * than deleting what they typed, so retrying is just dispatching the transcript again.
     */
    fun retryLast() {
        if (state.value.isAwaitingReply) return
        if (state.value.messages.lastOrNull()?.role != ChatRole.User) return
        dispatch()
    }

    /** Answers whatever user turn the transcript currently ends on. */
    private fun dispatch() {
        val messages = state.value.messages
        val question = messages.lastOrNull()?.takeIf { it.role == ChatRole.User } ?: return
        val history = messages.dropLast(1)

        askJob?.cancel()
        askJob = viewModelScope.launch {
            state.update {
                it.copy(
                    isAwaitingReply = true,
                    error = null,
                    rateLimited = false,
                    suggestions = emptyList()
                )
            }
            persist()

            // Never answer from a half-built context. Usually already complete — the coin screen
            // filled the cache seconds ago — so this is a no-op that costs nothing.
            contextJob?.join()

            // Best-effort, so an answer quotes the price as it is now rather than as it was when
            // the screen opened. Failure keeps the last known ticker.
            refreshTicker()

            when (
                val result = chatService.ask(
                    symbol = symbol,
                    baseAsset = baseAsset,
                    ticker = state.value.ticker,
                    news = news,
                    overview = overview,
                    history = history,
                    question = question.text,
                    apiToken = apiToken
                )
            ) {
                is CoinChatService.ChatResult.Success -> {
                    val reply = CoinChatMessage(
                        id = question.id + 1,
                        role = ChatRole.Assistant,
                        text = result.text
                    )
                    state.update {
                        val updated = it.messages + reply
                        it.copy(
                            messages = updated,
                            isAwaitingReply = false,
                            error = null,
                            rateLimited = false,
                            suggestions = suggestions(updated)
                        )
                    }
                    persist()
                }

                // Routine at the anonymous tier, so it is shown as a retryable notice under the
                // question rather than as an error that discards the turn.
                is CoinChatService.ChatResult.RateLimited -> state.update {
                    it.copy(
                        isAwaitingReply = false,
                        rateLimited = true,
                        suggestions = suggestions(it.messages)
                    )
                }

                is CoinChatService.ChatResult.Failure -> state.update {
                    it.copy(
                        isAwaitingReply = false,
                        error = result.message,
                        suggestions = suggestions(it.messages)
                    )
                }
            }
        }
    }

    fun clearConversation() {
        askJob?.cancel()
        viewModelScope.launch {
            CoinChatStore.clear(symbol)
            state.update {
                it.copy(
                    messages = emptyList(),
                    isAwaitingReply = false,
                    error = null,
                    rateLimited = false,
                    suggestions = suggestions(emptyList())
                )
            }
        }
    }

    private suspend fun persist() {
        CoinChatStore.put(symbol, state.value.messages)
    }

    private fun suggestions(messages: List<CoinChatMessage>): List<ChatSuggestion> {
        val isFirstTurn = messages.isEmpty()
        return suggestionsFor(
            baseAsset = baseAsset,
            ticker = state.value.ticker,
            hasNews = news.isNotEmpty(),
            hasOverview = !overview.isNullOrBlank(),
            isFirstTurn = isFirstTurn,
            max = if (isFirstTurn) MAX_SUGGESTIONS else MAX_FOLLOW_UPS
        )
    }

    /**
     * Re-reads the 24h ticker unless one was fetched within [TICKER_FRESH_MILLIS].
     *
     * Opening the chat from a chip fetches once while resolving context and again on the way into
     * the question, milliseconds apart; a burst of quick follow-ups would do the same. The guard
     * collapses those without making any answer quote a meaningfully old price.
     */
    private suspend fun refreshTicker() {
        val now = Clock.System.now().toEpochMilliseconds()
        val age = now - lastTickerFetchMillis
        if (state.value.ticker != null && age in 0 until TICKER_FRESH_MILLIS) return

        val fresh = fetchTicker(symbol) ?: return
        lastTickerFetchMillis = now
        state.update { it.copy(ticker = fresh) }
    }

    private suspend fun fetchTicker(symbol: String): Ticker24hr? = try {
        httpClient.fetchTicker24hr(symbol)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.logger.w(throwable = e) { "CoinChatViewModel: ticker fetch failed for $symbol" }
        null
    }

    private suspend fun fetchNews(symbol: String, enabledRssProviders: Set<String>): List<NewsItem> {
        if (enabledRssProviders.isEmpty()) return emptyList()
        return try {
            coroutineScope {
                RssProvider.ALL_PROVIDERS
                    .filter { it.id in enabledRssProviders }
                    .map { provider ->
                        async {
                            try {
                                // News is background context for the chat rather than the
                                // deliverable, so an unreachable provider degrades quietly: the
                                // model still answers from the ticker and the feeds that did land.
                                val result = newsService.fetchNewsFromProvider(provider, symbol)
                                (result as? NewsFetchResult.Success)?.items.orEmpty()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                // Throwable, not Exception — see NewsService.fetchRSSFeed.
                                AppLogger.logger.w(throwable = e) {
                                    "CoinChatViewModel: news fetch failed for ${provider.name}"
                                }
                                emptyList()
                            }
                        }
                    }
                    .awaitAll()
                    .flatten()
                    .distinctBy { it.link }
                    // Same ordering the coin screen applies, so the model sees the same headlines
                    // in the same order the user does.
                    .sortedWith(
                        compareByDescending<NewsItem> { item ->
                            try {
                                ktx.parseRssDate(item.pubDate)
                            } catch (_: Exception) {
                                kotlin.time.Instant.fromEpochMilliseconds(0)
                            }
                        }.thenBy { it.link }
                    )
                    .take(AiInsightService.MAX_NEWS_ITEMS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLogger.logger.e(throwable = e) { "CoinChatViewModel: news fetch failed for $symbol" }
            emptyList()
        }
    }

    companion object {
        /** How long a fetched 24h ticker is treated as current. */
        const val TICKER_FRESH_MILLIS = 10_000L
    }

    override fun onCleared() {
        super.onCleared()
        startJob?.cancel()
        contextJob?.cancel()
        askJob?.cancel()
        httpClient.close()
        newsService.close()
        chatService.close()
    }
}
