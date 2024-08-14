package ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import model.CryptoPair
import model.Ticker
import model.TickerData
import model.UiKline
import network.HttpClient
import network.WebSocketClient
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun CryptoList() {
    val webSocketClient = remember { WebSocketClient() }
    val httpClient = remember { HttpClient() }
    val coroutineScope = rememberCoroutineScope()
    var trades by remember { mutableStateOf(emptyMap<String, List<UiKline>>()) }
    var tickerDataMap by remember { mutableStateOf(emptyMap<String, TickerData>()) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val webSocketJob = coroutineScope.launch(Dispatchers.IO) {
            webSocketClient.connect()
            webSocketClient.getIncomingMessages().collectLatest { message ->
                runCatching {
                    val tickers = Json.decodeFromString<List<Ticker>>(message)
                    val updatedMap = buildMap {
                        putAll(tickerDataMap)
                        tickers.filter { CryptoPair.getAllPairs().contains(it.symbol) }.forEach { ticker ->
                            put(
                                ticker.symbol, TickerData(
                                    symbol = ticker.symbol,
                                    lastPrice = formatPrice(ticker.lastPrice),
                                    priceChangePercent = ticker.priceChangePercent,
                                    timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString(),
                                    volume = ticker.totalTradedQuoteAssetVolume
                                )
                            )
                            trades = trades.toMutableMap().apply {
                                put(ticker.symbol, (this[ticker.symbol] ?: emptyList()) + listOf(UiKline(closePrice = ticker.lastPrice)))
                            }
                        }
                    }

                    tickerDataMap = updatedMap.toList()
                        .sortedByDescending { (_, value) -> value.volume.toDoubleOrNull() ?: 0.0 }
                        .toMap()
                    isLoading = false
                }.getOrElse {
                    it.printStackTrace()
                }
            }
        }
        val fetchTradesJob = coroutineScope.launch(Dispatchers.IO) {
            trades = httpClient.fetchUiKlines(CryptoPair.getAllPairs())
        }
        onDispose {
            webSocketJob.cancel()
            fetchTradesJob.cancel()
            webSocketClient.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn {
                items(tickerDataMap.values.toList()) { tickerData ->
                    trades[tickerData.symbol]?.let {
                        TickerCard(
                            symbol = tickerData.symbol,
                            price = tickerData.lastPrice,
                            timestamp = tickerData.timestamp,
                            trades = it
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TradeChart(
    trades: List<UiKline>
) {
    if (trades.isEmpty()) {
        Text(
            text = "No trade data available",
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    val prices = trades.map { it.closePrice.toFloat() }
    val maxPrice = prices.maxOrNull() ?: 0f
    val minPrice = prices.minOrNull() ?: 0f
    val priceRange = if (maxPrice != minPrice) maxPrice - minPrice else 1f

    var chartSize by remember { mutableStateOf(Offset.Zero) }
    val lineColor = MaterialTheme.colorScheme.onPrimary

    Box(modifier = Modifier.weight(1f)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp)

        ) {
            chartSize = Offset(size.width, size.height)
            val path = Path()
            val points = calculatePoints(trades, chartSize, minPrice, priceRange)

            points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.x, point.y)
                else path.lineTo(point.x, point.y)
            }

            // Draw the chart line
            drawPath(path = path, color = lineColor, style = Stroke(width = 3f))
        }
    }
}

fun calculatePoints(trades: List<UiKline>, size: Offset, minPrice: Float, priceRange: Float): List<Offset> {
    return trades.mapIndexed { index, trade ->
        val x = index.toFloat() / (trades.size - 1) * size.x
        val y = size.y - ((trade.closePrice.toFloat() - minPrice) / priceRange) * size.y
        Offset(x, y)
    }
}


@Composable
fun TickerCard(
    symbol: String,
    price: String,
    timestamp: String,
    trades: List<UiKline>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f)
            ) {
                if (symbol.isNotEmpty()) {
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                AnimatedContent(
                    targetState = price,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith fadeOut(animationSpec = tween(durationMillis = 300))
                    },
                    label = "Price Animation"
                ) { targetPrice ->
                    Text(
                        text = targetPrice,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            TradeChart(
                trades = trades
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                if (timestamp.isNotEmpty()) {
                    val localDateTime = try {
                        val instant = Instant.parse(timestamp)
                        instant.toLocalDateTime(TimeZone.currentSystemDefault())
                    } catch (e: Exception) {
                        LocalDateTime.parse(timestamp)
                    }
                    val date = "${localDateTime.year}-" +
                        "${localDateTime.monthNumber.toString().padStart(2, '0')}-" +
                        localDateTime.dayOfMonth.toString().padStart(2, '0')
                    val time = "${localDateTime.hour.toString().padStart(2, '0')}:" +
                        "${localDateTime.minute.toString().padStart(2, '0')}:" +
                        localDateTime.second.toString().padStart(2, '0')


                    Column(
                        modifier = Modifier.align(Alignment.End),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        AnimatedContent(
                            targetState = time,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith fadeOut(
                                    animationSpec = tween(
                                        durationMillis = 300
                                    )
                                )
                            },
                            label = "Time Animation"
                        ) { targetTime ->
                            Text(
                                text = targetTime,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatPrice(price: String): String = runCatching {
    val formattedPrice = price.toDouble().formatAsCurrency()
    "$$formattedPrice"
}.getOrElse { price }

fun Double.formatAsCurrency(): String {
    val absValue = this.absoluteValue
    val integerPart = absValue.toInt()
    val fractionalPart = ((absValue - integerPart) * 100).roundToInt()

    val formattedInteger = integerPart.toString().reversed().chunked(3).joinToString(",").reversed()
    val formattedFractional = fractionalPart.toString().padStart(2, '0')

    return if (this < 0) "-$formattedInteger.$formattedFractional" else "$formattedInteger.$formattedFractional"
}