package ui

import NetworkConnectivityObserver
import NetworkStatus
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
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import model.Ticker
import model.TickerData
import model.TickerDataInfo
import model.TradingPair
import model.UiKline
import network.HttpClient

class CryptoViewModel : ViewModel() {
    private val httpClient = HttpClient()
    private val webSocketClient = getWebSocketClient()

    private val _trades = MutableStateFlow<Map<String, List<UiKline>>>(emptyMap())
    val trades: StateFlow<Map<String, List<UiKline>>> = _trades.asStateFlow()

    private val _symbols = MutableStateFlow<List<TickerDataInfo>>(emptyList())

    private val _tradingPairs = MutableStateFlow<List<TradingPair>>(emptyList())
    val tradingPairs: StateFlow<List<TradingPair>> = _tradingPairs.asStateFlow()

    private val _selectedTradingPair = MutableStateFlow("BTC")
    val selectedTradingPair: StateFlow<String> = _selectedTradingPair.asStateFlow()

    private val _allTickerDataMap = MutableStateFlow<Map<String, TickerData>>(emptyMap())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var webSocketJob: Job? = null
    private var lastUpdateTime = 0L
    private val updateThrottleMs = 250L

    val filteredTickerDataMap: StateFlow<Map<String, TickerData>> = combine(
        _allTickerDataMap,
        _selectedTradingPair,
        _searchQuery
    ) { allData, selectedPair, query ->
        val trimmedQuery = query.trim().uppercase()
        if (trimmedQuery.isEmpty()) {
            allData.filterKeys { it.endsWith(selectedPair) }
        } else {
            allData.filterKeys {
                it.endsWith(selectedPair) &&
                    it.replace(selectedPair, "").contains(trimmedQuery)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyMap()
    )

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
            _selectedTradingPair.value = pair
            _isLoading.value = true
            _searchQuery.value = ""

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

    fun fetchUiKlinesForVisibleSymbols(visibleSymbols: List<String>) {
        viewModelScope.launch {
            val currentTrades = _trades.value.keys

            val newSymbols = visibleSymbols.filterNot { it in currentTrades }

            if (newSymbols.isNotEmpty()) {
                val newData = httpClient.fetchUiKlines(newSymbols)

                _trades.value = _trades.value.filterKeys { it in visibleSymbols } + newData
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
        viewModelScope.launch {
            try {
                val marginSymbols = httpClient.fetchMarginSymbols()
                _tradingPairs.value = marginSymbols?.data?.distinctBy { it.quote } ?: emptyList()
                _symbols.value = marginSymbols?.data?.distinctBy { it.symbol }?.map {
                    TickerDataInfo(symbol = it.symbol, quoteVolume = "0", quote = it.quote)
                } ?: emptyList()
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
            launch {
                while (isActive) {
                    try {
                        webSocketClient.wss(
                            method = HttpMethod.Get,
                            host = "stream.binance.com",
                            path = "/ws/!ticker@arr",
                            request = { header(HttpHeaders.ContentType, ContentType.Application.Json) }
                        ) {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> withContext(Dispatchers.Default) {
                                        processTickerMessage(frame.readText())
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

    private fun processTickerMessage(message: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastUpdateTime < updateThrottleMs) return
        lastUpdateTime = now

        viewModelScope.launch {
            runCatching {
                val tickers = Json.decodeFromString<List<Ticker>>(message)
                val updatedTickerMap = _allTickerDataMap.value.toMutableMap()

                tickers.forEach { ticker ->
                    updatedTickerMap[ticker.symbol] = TickerData(
                        symbol = ticker.symbol,
                        lastPrice = formatPrice(ticker.lastPrice),
                        priceChangePercent = ticker.priceChangePercent,
                        timestamp = Clock.System.now().toString(),
                        volume = ticker.totalTradedQuoteAssetVolume
                    )
                }

                _allTickerDataMap.value = updatedTickerMap
                updateDisplayedPairs()
            }.onFailure {
                println("Error processing ticker message: ${it.message}")
            }
        }
    }

    override fun onCleared() {
        webSocketJob?.cancel()
        webSocketClient.close()
        super.onCleared()
    }

    fun ensureChartData(symbol: String) {
        viewModelScope.launch {
            if (!_trades.value.containsKey(symbol)) {
                val newData = httpClient.fetchUiKlines(listOf(symbol))
                _trades.value += newData
            }
        }
    }
}