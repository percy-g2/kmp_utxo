package ui

import NetworkConnectivityObserver
import NetworkStatus
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import getWebSocketClient
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ktx.formatPrice
import model.SortParams
import model.Ticker
import model.TickerData
import model.TickerDataInfo
import model.TradingPair
import model.UiKline
import network.HttpClient
import theme.ThemeManager.store
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CryptoViewModel : ViewModel() {
    private val httpClient = HttpClient()
    private val webSocketClient = getWebSocketClient()

    private val _trades = MutableStateFlow<Map<String, List<UiKline>>>(emptyMap())
    val trades: StateFlow<Map<String, List<UiKline>>> = _trades.asStateFlow()

    private val _symbols = MutableStateFlow<List<TickerDataInfo>>(emptyList())

    private val _tradingPairs = MutableStateFlow<List<TradingPair>>(emptyList())
    val tradingPairs: StateFlow<List<TradingPair>> = _tradingPairs.asStateFlow()

    private val _allTickerDataMap = MutableStateFlow<Map<String, TickerData>>(emptyMap())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var webSocketJob: Job? = null
    private var lastUpdateTime = 0L
    private val updateThrottleMs = 250L
    private val settings = store.updates

    val currentSortKey = mutableStateOf(SortParams.Vol)
    val isSortDesc = mutableStateOf(false)

    val filteredTickerDataMap: StateFlow<Map<String, TickerData>> = combine(
        _allTickerDataMap,
        settings,
        _searchQuery,
        snapshotFlow { currentSortKey.value },
        snapshotFlow { isSortDesc.value }
    ) { allData, settings, query, sortKey, isDescending ->
        val selectedPair = settings?.selectedTradingPair ?: "BTC"
        val trimmedQuery = query.trim().uppercase()
        
        // Optimize filtering - avoid creating intermediate collections
        val filtered = if (trimmedQuery.isEmpty()) {
            allData.filterKeys { it.endsWith(selectedPair) }
        } else {
            allData.filterKeys {
                it.endsWith(selectedPair) &&
                    it.replace(selectedPair, "").contains(trimmedQuery)
            }
        }

        val tickerList = filtered.values.toMutableList()

        // Pre-compute numeric values for sorting to avoid repeated parsing during sort
        when (sortKey) {
            SortParams.Pair -> {
                if (!isDescending) {
                    tickerList.sortByDescending { it.symbol }
                } else {
                    tickerList.sortBy { it.symbol }
                }
            }
            SortParams.Vol -> {
                // Pre-compute all volumes once
                val volumeMap = tickerList.associateWith { it.volume.toDoubleOrNull() ?: 0.0 }
                if (!isDescending) {
                    tickerList.sortByDescending { volumeMap[it] ?: 0.0 }
                } else {
                    tickerList.sortBy { volumeMap[it] ?: 0.0 }
                }
            }
            SortParams.Price -> {
                // Pre-compute all prices once - cache regex-like filtering
                val priceMap = tickerList.associateWith {
                    it.lastPrice.filter { char -> char.isDigit() || char == '.' }.toDoubleOrNull() ?: 0.0
                }
                if (!isDescending) {
                    tickerList.sortByDescending { priceMap[it] ?: 0.0 }
                } else {
                    tickerList.sortBy { priceMap[it] ?: 0.0 }
                }
            }
            SortParams.Change -> {
                // Pre-compute all change values once
                val changeMap = tickerList.associateWith { it.priceChangePercent.toDoubleOrNull() ?: 0.0 }
                if (!isDescending) {
                    tickerList.sortByDescending { changeMap[it] ?: 0.0 }
                } else {
                    tickerList.sortBy { changeMap[it] ?: 0.0 }
                }
            }
        }
        tickerList.associateBy { it.symbol }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyMap()
    )

    val favoritesTickerDataMap: StateFlow<Map<String, TickerData>> = combine(
        _allTickerDataMap,
        settings
    ) { allData: Map<String, TickerData>, settings: Settings? ->
        val favorites = settings?.favPairs ?: emptyList()
        allData.filterKeys { it in favorites }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyMap()
    )

    fun addToFavorites(symbol: String) {
        viewModelScope.launch {
            store.update { current ->
                val prevFavs = current?.favPairs ?: emptyList()
                if (symbol !in prevFavs && current is Settings) current.copy(favPairs = prevFavs + symbol)
                else current
            }
        }
    }

    fun removeFromFavorites(symbol: String) {
        viewModelScope.launch {
            store.update { current ->
                val prevFavs = current?.favPairs ?: emptyList()
                if (symbol in prevFavs && current is Settings) current.copy(favPairs = prevFavs - symbol)
                else current
            }
        }
    }

    init {
        initializeData()
        observeNetworkChanges()
    }

    fun setSearchQuery(query: String) {
        viewModelScope.launch {
            _searchQuery.value = query
        }
    }

    fun setSelectedTradingPair(pair: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _searchQuery.value = ""

            store.update { currentSettings ->
                currentSettings?.copy(selectedTradingPair = pair)
                    ?: Settings(selectedTradingPair = pair)
            }

            if (filteredTickerDataMap.value.isNotEmpty()) {
                _isLoading.value = false
            }
        }
    }

    private fun updateDisplayedPairs() {
        viewModelScope.launch {
            if (filteredTickerDataMap.value.isNotEmpty()) {
                _isLoading.value = false
            }
        }
    }

    fun updateSortKey(key: SortParams) {
        viewModelScope.launch {
            isSortDesc.value = if (currentSortKey.value == key) !isSortDesc.value else isSortDesc.value
            currentSortKey.value = key
        }
    }

    private var fetchJob: Job? = null

    fun fetchUiKlinesForVisibleSymbols(
        visibleSymbols: List<String>,
        forceRefresh: Boolean = false
    ) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch(Dispatchers.Default) {
            if (!forceRefresh) {
                delay(100) // Debounce time
            }

            // Check current state on main thread, then switch to background
            val currentTrades = withContext(Dispatchers.Main) { _trades.value.keys }
            val newSymbols = if (forceRefresh) {
                withContext(Dispatchers.Main) {
                    _trades.value = emptyMap()
                }
                visibleSymbols
            } else {
                visibleSymbols.filterNot { symbol ->
                    symbol in currentTrades && (withContext(Dispatchers.Main) { _trades.value[symbol]?.size } ?: 0) > 50
                }
            }
            
            // Limit concurrent fetches to reduce memory pressure on iOS
            val limitedSymbols = newSymbols.take(10) // Fetch max 10 at a time

            if (limitedSymbols.isNotEmpty()) {
                try {
                    // I/O happens off main thread
                    val newData = httpClient.fetchUiKlines(limitedSymbols)
                    // Update state on main thread - only keep visible symbols to reduce memory
                    withContext(Dispatchers.Main) {
                        // Filter to only keep visible symbols + new data to reduce memory footprint
                        _trades.value = _trades.value.filterKeys { it in visibleSymbols } + newData
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("Error fetching klines: ${e.message}")
                }
            }
        }
    }


    private fun initializeData() {
        viewModelScope.launch {
            try {
                fetchMarginSymbols()
                startWebSocketConnection()
            } catch (e: Exception) {
                println("Error initializing data: ${e.message}")
            }
        }
    }

    private fun fetchMarginSymbols() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // I/O happens off main thread
                val marginSymbols = httpClient.fetchMarginSymbols()
                // Process data off main thread
                val tradingPairsList = marginSymbols?.data?.distinctBy { it.quote } ?: emptyList()
                val symbolsList = marginSymbols?.data?.distinctBy { it.symbol }?.map {
                    TickerDataInfo(symbol = it.symbol, quoteVolume = "0", quote = it.quote)
                } ?: emptyList()
                
                // Update state on main thread
                withContext(Dispatchers.Main) {
                    _tradingPairs.value = tradingPairsList
                    _symbols.value = symbolsList
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Error fetching margin symbols: ${e.message}")
            }
        }
    }

    private fun observeNetworkChanges() {
        val networkObserver = NetworkConnectivityObserver()
        viewModelScope.launch {
            networkObserver.observe().collectLatest { status ->
                if (status == NetworkStatus.Available) {
                    reconnect()
                }
            }
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            try {
                updateDisplayedPairs()
                startWebSocketConnection()
            } catch (e: Exception) {
                println("Error reconnecting: ${e.message}")
            }
        }
    }

    private fun startWebSocketConnection() {
        webSocketJob?.cancel()
        webSocketJob = viewModelScope.launch {
            connectWebSocket()
        }
    }

    private suspend fun connectWebSocket() {
        supervisorScope {
            launch(Dispatchers.Default) {
                while (isActive) {
                    try {
                        webSocketClient.wss(
                            method = HttpMethod.Get,
                            host = "stream.binance.com",
                            path = "/ws/!ticker@arr",
                            request = { header(HttpHeaders.ContentType, ContentType.Application.Json) }
                        ) {
                            for (frame in incoming) {
                                if (!isActive) break // Check cancellation
                                when (frame) {
                                    is Frame.Text -> {
                                        // Process message off main thread
                                        processTickerMessage(frame.readText())
                                    }
                                    is Frame.Ping -> send(Frame.Pong(frame.data))
                                    is Frame.Close -> throw CancellationException("WebSocket closed")
                                    else -> {} // no-op
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e // Re-throw cancellation to properly stop
                    } catch (e: Exception) {
                        if (isActive) {
                            println("WebSocket error: ${e.message}")
                            delay(5000)
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun processTickerMessage(message: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastUpdateTime < updateThrottleMs) return
        lastUpdateTime = now

        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                // Parse JSON off main thread
                val tickers = Json.decodeFromString<List<Ticker>>(message)
                val currentTradingPairs = tradingPairs.value
                
                // Process all data off main thread
                val updatedTickerMap = _allTickerDataMap.value.toMutableMap()
                val updatedTrades = _trades.value.toMutableMap()
                
                tickers.forEach { ticker ->
                    // Format price off main thread
                    val formattedPrice = ticker.lastPrice.formatPrice(ticker.symbol, currentTradingPairs)
                    updatedTickerMap[ticker.symbol] = TickerData(
                        symbol = ticker.symbol,
                        lastPrice = formattedPrice,
                        priceChangePercent = ticker.priceChangePercent,
                        volume = ticker.totalTradedQuoteAssetVolume
                    )
                    
                    // Update trades efficiently - limit to reduce memory pressure on iOS
                    // Keep only last 200 points (enough for smooth charts, reduces memory)
                    val MAX_TRADES_PER_SYMBOL = 200
                    val currentTrades = updatedTrades[ticker.symbol] ?: emptyList()
                    updatedTrades[ticker.symbol] = if (currentTrades.size >= MAX_TRADES_PER_SYMBOL) {
                        // Use drop + add instead of creating new list to reduce allocations
                        currentTrades.drop(1) + UiKline(closePrice = ticker.lastPrice)
                    } else {
                        currentTrades + UiKline(closePrice = ticker.lastPrice)
                    }
                }

                // Update state on main thread - limit trades to visible symbols only to reduce memory
                withContext(Dispatchers.Main) {
                    _allTickerDataMap.value = updatedTickerMap
                    // Only keep trades with enough data points, and limit to reduce memory pressure
                    _trades.value = updatedTrades.filterValues { it.size > 50 }
                    updateDisplayedPairs()
                }
            }.onFailure {
                println("Error processing ticker message: ${it.message}")
            }
        }
    }

    override fun onCleared() {
        // Cancel all background jobs to prevent memory leaks
        webSocketJob?.cancel()
        fetchJob?.cancel()
        webSocketClient.close()
        super.onCleared()
    }

    fun ensureChartData(symbol: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Check state on main thread
                val needsFetch = withContext(Dispatchers.Main) {
                    !_trades.value.containsKey(symbol)
                }
                
                if (needsFetch) {
                    // I/O happens off main thread
                    val newData = httpClient.fetchUiKlines(listOf(symbol))
                    // Update state on main thread
                    withContext(Dispatchers.Main) {
                        _trades.value += newData
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Error ensuring chart data: ${e.message}")
            }
        }
    }
}