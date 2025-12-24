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
import logging.AppLogger
import model.SortParams
import model.Ticker
import model.TickerData
import model.TickerDataInfo
import model.TradingPair
import model.UiKline
import network.HttpClient
import theme.ThemeManager.store
import syncSettingsToWidget
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CryptoViewModel : ViewModel() {
    private val httpClient = HttpClient()
    private val webSocketClient = getWebSocketClient()
    private var isWebSocketConnected = false

    val trades: StateFlow<Map<String, List<UiKline>>>
        field = MutableStateFlow(emptyMap())

    val symbols: StateFlow<List<TickerDataInfo>>
        field = MutableStateFlow(emptyList())

    val tradingPairs: StateFlow<List<TradingPair>>
        field = MutableStateFlow(emptyList())

    val allTickerDataMap: StateFlow<Map<String, TickerData>>
        field = MutableStateFlow(emptyMap())

    val isLoading: StateFlow<Boolean>
        field = MutableStateFlow(true)

    val searchQuery: StateFlow<String>
        field = MutableStateFlow("")

    private var webSocketJob: Job? = null
    private var lastUpdateTime = 0L
    private val updateThrottleMs = 250L
    private val settings = store.updates

    val currentSortKey = mutableStateOf(SortParams.Vol)
    val isSortDesc = mutableStateOf(false)

    val filteredTickerDataMap: StateFlow<Map<String, TickerData>> = combine(
        allTickerDataMap,
        settings,
        searchQuery,
        snapshotFlow { currentSortKey.value },
        snapshotFlow { isSortDesc.value }
    ) { allData, settings, query, sortKey, isDescending ->
        val selectedPair = settings?.selectedTradingPair ?: "BTC"
        val favorites = settings?.favPairs ?: emptyList()
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
        
        // Separate favorites and non-favorites
        val (favoritesList, nonFavoritesList) = tickerList.partition { it.symbol in favorites }

        // Helper function to sort a list based on sortKey
        fun sortTickerList(list: List<TickerData>): List<TickerData> {
            val sortedList = list.toMutableList()
            when (sortKey) {
                SortParams.Pair -> {
                    if (!isDescending) {
                        sortedList.sortByDescending { it.symbol }
                    } else {
                        sortedList.sortBy { it.symbol }
                    }
                }
                SortParams.Vol -> {
                    // Pre-compute all volumes once
                    val volumeMap = sortedList.associateWith { it.volume.toDoubleOrNull() ?: 0.0 }
                    if (!isDescending) {
                        sortedList.sortByDescending { volumeMap[it] ?: 0.0 }
                    } else {
                        sortedList.sortBy { volumeMap[it] ?: 0.0 }
                    }
                }
                SortParams.Price -> {
                    // Pre-compute all prices once - cache regex-like filtering
                    val priceMap = sortedList.associateWith {
                        it.lastPrice.filter { char -> char.isDigit() || char == '.' }.toDoubleOrNull() ?: 0.0
                    }
                    if (!isDescending) {
                        sortedList.sortByDescending { priceMap[it] ?: 0.0 }
                    } else {
                        sortedList.sortBy { priceMap[it] ?: 0.0 }
                    }
                }
                SortParams.Change -> {
                    // Pre-compute all change values once
                    val changeMap = sortedList.associateWith { it.priceChangePercent.toDoubleOrNull() ?: 0.0 }
                    if (!isDescending) {
                        sortedList.sortByDescending { changeMap[it] ?: 0.0 }
                    } else {
                        sortedList.sortBy { changeMap[it] ?: 0.0 }
                    }
                }
            }
            return sortedList
        }

        // Sort favorites first, then non-favorites, and combine
        val sortedFavorites = sortTickerList(favoritesList)
        val sortedNonFavorites = sortTickerList(nonFavoritesList)
        val finalList = sortedFavorites + sortedNonFavorites
        
        finalList.associateBy { it.symbol }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyMap()
    )

    val favoritesTickerDataMap: StateFlow<Map<String, TickerData>> = combine(
        allTickerDataMap,
        settings,
        symbols
    ) { allData: Map<String, TickerData>, settings: Settings?, symbols: List<TickerDataInfo> ->
        val favorites = settings?.favPairs ?: emptyList()
        val favoritesFromAllData = allData.filterKeys { it in favorites }
        
        // If a favorite is not in allData yet (WebSocket hasn't received it),
        // create a placeholder entry from symbols list to ensure it shows up
        val missingFavorites = favorites.filterNot { it in favoritesFromAllData.keys }
        val placeholderEntries = missingFavorites.mapNotNull { symbol ->
            symbols.find { it.symbol == symbol }?.let { symbolInfo ->
                symbol to TickerData(
                    symbol = symbolInfo.symbol,
                    lastPrice = "0.00",
                    priceChangePercent = "0.00",
                    volume = symbolInfo.quoteVolume
                )
            }
        }
        
        // Combine all favorites and sort by volume (descending - highest volume first)
        val allFavorites = (favoritesFromAllData + placeholderEntries).values.toList()
        val sortedFavorites = allFavorites.sortedByDescending { 
            it.volume.toDoubleOrNull() ?: 0.0 
        }
        
        sortedFavorites.associateBy { it.symbol }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyMap()
    )

    fun addToFavorites(symbol: String) {
        viewModelScope.launch {
            var updatedSettings: Settings? = null
            store.update { current ->
                val prevFavs = current?.favPairs ?: emptyList()
                if (symbol !in prevFavs && current is Settings) {
                    updatedSettings = current.copy(favPairs = prevFavs + symbol)
                    updatedSettings
                } else {
                    current
                }
            }
            // Refresh widget immediately when favorites change
            updatedSettings?.let { syncSettingsToWidget(it) }
        }
    }

    fun removeFromFavorites(symbol: String) {
        viewModelScope.launch {
            var updatedSettings: Settings? = null
            store.update { current ->
                val prevFavs = current?.favPairs ?: emptyList()
                if (symbol in prevFavs && current is Settings) {
                    updatedSettings = current.copy(favPairs = prevFavs - symbol)
                    updatedSettings
                } else {
                    current
                }
            }
            // Refresh widget immediately when favorites change
            updatedSettings?.let { syncSettingsToWidget(it) }
        }
    }

    init {
        initializeData()
        observeNetworkChanges()
    }

    fun setSearchQuery(query: String) {
        viewModelScope.launch {
            searchQuery.value = query
        }
    }

    fun setSelectedTradingPair(pair: String) {
        viewModelScope.launch {
            isLoading.value = true
            searchQuery.value = ""

            store.update { currentSettings ->
                currentSettings?.copy(selectedTradingPair = pair)
                    ?: Settings(selectedTradingPair = pair)
            }

            if (filteredTickerDataMap.value.isNotEmpty()) {
                isLoading.value = false
            }
        }
    }

    private fun updateDisplayedPairs() {
        viewModelScope.launch {
            if (filteredTickerDataMap.value.isNotEmpty()) {
                isLoading.value = false
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
            val currentTrades = withContext(Dispatchers.Main) { trades.value.keys }
            val newSymbols = if (forceRefresh) {
                withContext(Dispatchers.Main) {
                    trades.value = emptyMap()
                }
                visibleSymbols
            } else {
                visibleSymbols.filterNot { symbol ->
                    symbol in currentTrades && (withContext(Dispatchers.Main) { trades.value[symbol]?.size } ?: 0) > 50
                }
            }
            
            // Process all visible symbols in batches to ensure charts load for all visible items
            // Process in batches of 10 to reduce memory pressure on iOS
            val batchSize = 10
            newSymbols.chunked(batchSize).forEach { batch ->
                if (batch.isNotEmpty()) {
                    try {
                        // I/O happens off main thread
                        val newData = httpClient.fetchUiKlines(batch)
                        // Update state on main thread - only keep visible symbols + new data to reduce memory footprint
                        withContext(Dispatchers.Main) {
                            trades.value = trades.value.filterKeys { it in visibleSymbols } + newData
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.logger.e(throwable = e) { "Error fetching klines for batch" }
                    }
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
                AppLogger.logger.e(throwable = e) { "Error initializing data" }
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
                    tradingPairs.value = tradingPairsList
                    symbols.value = symbolsList
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logger.e(throwable = e) { "Error fetching margin symbols" }
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
                AppLogger.logger.e(throwable = e) { "Error reconnecting" }
            }
        }
    }
    
    fun pause() {
        // Stop WebSocket connection to prevent data accumulation
        webSocketJob?.cancel()
        isWebSocketConnected = false
        
        // Clear trades data to free memory when not on Market screen
        viewModelScope.launch {
            trades.value = emptyMap()
        }
    }
    
    fun resume() {
        // Resume WebSocket connection when returning to Market screen
        if (!isWebSocketConnected) {
            startWebSocketConnection()
        }
    }
    
    fun fetchFavoritesPrices(favoriteSymbols: List<String>) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Find favorites that don't have real data yet (have placeholder "0.00" price)
                val favoritesNeedingData = favoriteSymbols.filter { symbol ->
                    val currentData = allTickerDataMap.value[symbol]
                    currentData == null || currentData.lastPrice == "0.00"
                }
                
                if (favoritesNeedingData.isNotEmpty()) {
                    // Fetch current ticker data for favorites that need it
                    val tickers = httpClient.fetchTickers24hr(favoritesNeedingData)
                    val currentTradingPairs = tradingPairs.value
                    
                    // Update allTickerDataMap with fetched data
                    val updatedTickerMap = allTickerDataMap.value.toMutableMap()
                    tickers.forEach { (symbol, ticker) ->
                        val formattedPrice = ticker.lastPrice.formatPrice(symbol, currentTradingPairs)
                        updatedTickerMap[symbol] = TickerData(
                            symbol = symbol,
                            lastPrice = formattedPrice,
                            priceChangePercent = ticker.priceChangePercent,
                            volume = ticker.quoteVolume
                        )
                    }
                    
                    withContext(Dispatchers.Main) {
                        allTickerDataMap.value = updatedTickerMap
                    }
                }
            } catch (e: CancellationException) {
                AppLogger.logger.d(throwable = e) { "Cancelled fetching favorites prices" }
            } catch (e: Exception) {
                AppLogger.logger.e(throwable = e) { "Error fetching favorites prices" }
            }
        }
    }

    private fun startWebSocketConnection() {
        // Cancel existing connection before starting a new one
        webSocketJob?.cancel()
        isWebSocketConnected = false
        
        // Wait a bit for the previous connection to fully close
        webSocketJob = viewModelScope.launch {
            delay(100) // Small delay to ensure previous connection is closed
            connectWebSocket()
        }
    }

    private suspend fun connectWebSocket() {
        // Use a single coroutine scope to ensure only one connection at a time
        supervisorScope {
            launch(Dispatchers.Default) {
                while (isActive) {
                    try {
                        isWebSocketConnected = true
                        webSocketClient.wss(
                            method = HttpMethod.Get,
                            host = "stream.binance.com",
                            path = "/ws/!ticker@arr",
                            request = { header(HttpHeaders.ContentType, ContentType.Application.Json) }
                        ) {
                            for (frame in incoming) {
                                if (!isActive) {
                                    isWebSocketConnected = false
                                    break // Check cancellation
                                }
                                when (frame) {
                                    is Frame.Text -> {
                                        // Process message off main thread
                                        processTickerMessage(frame.readText())
                                    }
                                    is Frame.Ping -> send(Frame.Pong(frame.data))
                                    is Frame.Close -> {
                                        isWebSocketConnected = false
                                        throw CancellationException("WebSocket closed")
                                    }
                                    else -> {} // no-op
                                }
                            }
                        }
                        isWebSocketConnected = false
                    } catch (e: CancellationException) {
                        isWebSocketConnected = false
                        AppLogger.logger.d(throwable = e) { "WebSocket connection cancelled" }
                    } catch (e: Exception) {
                        isWebSocketConnected = false
                        if (isActive) {
                            // Don't log network resolution errors as errors - they're expected when network is unavailable
                            // Check error message for network-related errors (cross-platform compatible)
                            val errorMessage = e.message?.lowercase() ?: ""
                            val isNetworkError = errorMessage.contains("unresolved", ignoreCase = true) ||
                                    errorMessage.contains("unknown host", ignoreCase = true) ||
                                    errorMessage.contains("connect", ignoreCase = true) ||
                                    errorMessage.contains("network", ignoreCase = true) ||
                                    errorMessage.contains("unreachable", ignoreCase = true)
                            
                            if (isNetworkError) {
                                AppLogger.logger.d(throwable = e) { "WebSocket network error (expected when offline)" }
                            } else {
                                AppLogger.logger.e(throwable = e) { "WebSocket error" }
                            }
                            delay(5000) // Wait before reconnecting
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
                val updatedTickerMap = allTickerDataMap.value.toMutableMap()
                val updatedTrades = trades.value.toMutableMap()
                
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
                    val maxTradesPerSymbol = 200
                    val currentTrades = updatedTrades[ticker.symbol] ?: emptyList()
                    updatedTrades[ticker.symbol] = if (currentTrades.size >= maxTradesPerSymbol) {
                        // Use drop + add instead of creating new list to reduce allocations
                        currentTrades.drop(1) + UiKline(closePrice = ticker.lastPrice)
                    } else {
                        currentTrades + UiKline(closePrice = ticker.lastPrice)
                    }
                }

                // Update state on main thread - limit trades to visible symbols only to reduce memory
                withContext(Dispatchers.Main) {
                    allTickerDataMap.value = updatedTickerMap
                    // Only keep trades with enough data points, and limit to reduce memory pressure
                    trades.value = updatedTrades.filterValues { it.size > 50 }
                    updateDisplayedPairs()
                }
            }.onFailure {
                AppLogger.logger.e(throwable = it) { "Error processing ticker message" }
            }
        }
    }

    override fun onCleared() {
        // Cancel all background jobs to prevent memory leaks
        webSocketJob?.cancel()
        fetchJob?.cancel()

        // Close HTTP client (this is per-ViewModel instance)
        try {
            viewModelScope.launch {
                try {
                    httpClient.close()
                } catch (_: Exception) {
                    // Ignore errors during cleanup
                }
            }
        } catch (_: Exception) {
            // Ignore errors during cleanup
        }
        
        super.onCleared()
    }

    fun ensureChartData(symbol: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Check state on main thread
                val needsFetch = withContext(Dispatchers.Main) {
                    !trades.value.containsKey(symbol)
                }
                
                if (needsFetch) {
                    // I/O happens off main thread
                    val newData = httpClient.fetchUiKlines(listOf(symbol))
                    // Update state on main thread
                    withContext(Dispatchers.Main) {
                        trades.value += newData
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logger.e(throwable = e) { "Error ensuring chart data" }
            }
        }
    }
}