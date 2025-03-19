package ui

import NetworkConnectivityObserver
import NetworkStatus
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import getWebSocketClient
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import model.Ticker
import model.TickerData
import model.TickerDataInfo
import model.UiKline
import network.HttpClient
import theme.ThemeManager

class CryptoViewModel : ViewModel() {
    private val httpClient = HttpClient()
    private val webSocketClient = getWebSocketClient()

    private val _trades = MutableStateFlow<Map<String, List<UiKline>>>(emptyMap())
    val trades: StateFlow<Map<String, List<UiKline>>> = _trades.asStateFlow()

    private val _symbols = MutableStateFlow<List<TickerDataInfo>>(emptyList())
    val symbols: StateFlow<List<TickerDataInfo>> = _symbols.asStateFlow()

    private val _tickerDataMap = MutableStateFlow<Map<String, TickerData>>(emptyMap())
    val tickerDataMap: StateFlow<Map<String, TickerData>> = _tickerDataMap.asStateFlow()

    val quoteCoinList = mutableStateListOf<String>()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val store = ThemeManager.store
    private var webSocketJob: Job? = null
    private val favPairsFlow = MutableStateFlow<List<String>>(emptyList())
    private var lastUpdateTime = 0L

    // Collect updates from store only once
    private var storeUpdateJob: Job? = null
    private val updateThrottleMs = 250L // Throttle updates to reduce UI redraws

    private var networkObserver: NetworkConnectivityObserver? = null
    private var networkObserverJob: Job? = null

    init {
        initializeData()
        observeNetworkChanges()
    }

    // Add this method to observe network changes
    private fun observeNetworkChanges() {
        networkObserver = NetworkConnectivityObserver()
        networkObserverJob = viewModelScope.launch {
            networkObserver?.observe()?.collectLatest { status ->
                if (status == NetworkStatus.Available) {
                    // Network is now available, reconnect
                    reconnect()
                }
            }
        }
    }

    private fun initializeData() {
        viewModelScope.launch {
            try {
                // Get initial settings
                store.get()?.let { settings ->
                    favPairsFlow.value = settings.favPairs.ifEmpty { listOf("BTCUSDT") }
                }

                // Start collecting updates from store
                startCollectingFavPairs()

                // Load initial data
                loadInitialData()

                // Start WebSocket connection
                startWebSocketConnection()

                // Fetch symbols
                fetchSymbols()
            } catch (e: Exception) {
                // Log the error
                println("Error initializing data: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    private fun startCollectingFavPairs() {
        storeUpdateJob = viewModelScope.launch {
            store.updates.collectLatest { settings ->
                val newFavPairs = settings?.favPairs ?: listOf("BTCUSDT")
                if (newFavPairs != favPairsFlow.value) {
                    // Find newly added pairs
                    val existingPairs = favPairsFlow.value
                    val addedPairs = newFavPairs.filter { it !in existingPairs }

                    // Update the favorite pairs flow value
                    favPairsFlow.value = newFavPairs

                    // Filter displayed data to only show favorite pairs
                    _tickerDataMap.value = _tickerDataMap.value.filterKeys { it in newFavPairs }
                    _trades.value = _trades.value.filterKeys { it in newFavPairs }

                    // Fetch data for newly added pairs
                    if (addedPairs.isNotEmpty()) {
                        viewModelScope.launch {
                            try {
                                val newTradesData = httpClient.fetchUiKlines(addedPairs)
                                // Merge new data with existing data
                                _trades.value += newTradesData
                            } catch (e: Exception) {
                                println("Error loading data for new pairs: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fetchSymbols() {
        viewModelScope.launch {
            try {
                _symbols.value = httpClient.fetchBinancePairs()
            } catch (e: Exception) {
                println("Error fetching symbols: ${e.message}")
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
            launch {
                while (isActive) {
                    try {
                        webSocketClient.wss(
                            method = HttpMethod.Get,
                            host = "stream.binance.com",
                            path = "/ws/!ticker@arr",
                            request = {
                                header(HttpHeaders.ContentType, ContentType.Application.Json)
                            }
                        ) {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val message = frame.readText()
                                        withContext(Dispatchers.Default) {
                                            processTickerMessage(message)
                                        }
                                    }
                                    is Frame.Ping -> send(Frame.Pong(frame.data))
                                    is Frame.Close -> throw CancellationException("WebSocket closed")
                                    else -> {} // no-op
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        println("WebSocket error: ${e.message}")
                        delay(5000)
                    }
                }
            }
        }
    }

    // In CryptoViewModel.kt
    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                val symbols = favPairsFlow.value.ifEmpty { listOf("BTCUSDT") }
                // Load initial trade data
                _trades.value = httpClient.fetchUiKlines(symbols)

                // Ensure we have initial ticker data for each symbol before removing loading state
                if (_tickerDataMap.value.isEmpty()) {
                    // Create placeholder ticker data until real data arrives from WebSocket
                    val initialTickerMap = symbols.associateWith { symbol ->
                        TickerData(
                            symbol = symbol,
                            lastPrice = "Loading...",
                            priceChangePercent = "0.0",
                            timestamp = Clock.System.now().toString(),
                            volume = "0"
                        )
                    }
                    _tickerDataMap.value = initialTickerMap
                }

                // Only set loading to false when we have both trades and ticker data
                if (_trades.value.isNotEmpty() && _tickerDataMap.value.isNotEmpty()) {
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                println("Error loading initial data: ${e.message}")
                _isLoading.value = false // Set to false on error so UI isn't stuck in loading state
            }
        }
    }

    private fun processTickerMessage(message: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        // Throttle updates to reduce UI redraws
        if (now - lastUpdateTime < updateThrottleMs) return
        lastUpdateTime = now

        viewModelScope.launch {
            runCatching {
                val tickers = Json.decodeFromString<List<Ticker>>(message)
                val favPairs = favPairsFlow.value

                val updatedMap = _tickerDataMap.value.toMutableMap()
                val updatedTrades = _trades.value.toMutableMap()

                tickers.filter { it.symbol in favPairs }.forEach { ticker ->
                    val newData = TickerData(
                        symbol = ticker.symbol,
                        lastPrice = formatPrice(ticker.lastPrice),
                        priceChangePercent = ticker.priceChangePercent,
                        timestamp = Clock.System.now().toString(),
                        volume = ticker.totalTradedQuoteAssetVolume
                    )

                    if (updatedMap[ticker.symbol] != newData) {
                        updatedMap[ticker.symbol] = newData
                    }

                    val currentTrades = updatedTrades[ticker.symbol] ?: emptyList()
                    if (currentTrades.size >= 100) {
                        updatedTrades[ticker.symbol] = currentTrades.drop(1) + UiKline(closePrice = ticker.lastPrice)
                    } else {
                        updatedTrades[ticker.symbol] = currentTrades + UiKline(closePrice = ticker.lastPrice)
                    }
                }

                withContext(Dispatchers.Main) {
                    _tickerDataMap.value = updatedMap
                    _trades.value = updatedTrades

                    // Only set loading to false when we have both trades and ticker data for at least one symbol
                    if (_trades.value.isNotEmpty() && _tickerDataMap.value.isNotEmpty()) {
                        _isLoading.value = false
                    }
                }
            }.onFailure {
                println("Error processing ticker message: ${it.message}")
                it.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            try {
                // Update favorite pairs
                store.get()?.let { settings ->
                    favPairsFlow.value = settings.favPairs.ifEmpty { listOf("BTCUSDT") }
                }

                // Reload data
                loadInitialData()

                // Restart WebSocket connection
                startWebSocketConnection()
            } catch (e: Exception) {
                println("Error reconnecting: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        // Cancel all jobs to prevent memory leaks
        webSocketJob?.cancel()
        storeUpdateJob?.cancel()
        networkObserverJob?.cancel()
        webSocketClient.close()
        super.onCleared()
    }
}