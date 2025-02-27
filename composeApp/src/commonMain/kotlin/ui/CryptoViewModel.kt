package ui

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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val store = ThemeManager.store
    private var webSocketJob: Job? = null
    private val favPairsFlow = MutableStateFlow<List<String>>(emptyList())
    private var lastUpdateTime = 0L

    init {
        viewModelScope.launch {
            updateFavPairs()
            loadInitialData()
            startWebSocketConnection()
            fetchSymbols()
        }
    }

    private suspend fun fetchSymbols() {
        _symbols.value = httpClient.fetchBinancePairs()
    }

    private suspend fun updateFavPairs() {
        favPairsFlow.value = store.get()?.favPairs ?: emptyList()
    }

    private suspend fun loadInitialData() {
        _trades.value = httpClient.fetchUiKlines(favPairsFlow.value)
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
                                            updateTickerData(message)
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
                        e.printStackTrace()
                        delay(5000)
                    }
                }
            }
        }
    }

    private fun updateTickerData(message: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastUpdateTime < 100) return
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
                    _isLoading.value = false
                }
            }.onFailure {
                it.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            updateFavPairs()
            _trades.value = httpClient.fetchUiKlines(favPairsFlow.value)
            startWebSocketConnection()
        }
    }

    override fun onCleared() {
        webSocketJob?.cancel()
        webSocketClient.close()
        super.onCleared()
    }
}