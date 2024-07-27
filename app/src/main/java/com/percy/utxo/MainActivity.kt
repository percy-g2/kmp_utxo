package com.percy.utxo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.percy.utxo.network.HttpClient
import com.percy.utxo.network.WebSocketClient
import com.percy.utxo.network.model.MarkPriceUpdate
import com.percy.utxo.network.model.Trade
import com.percy.utxo.ui.theme.UTXOTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UTXOTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WebSocketApp(innerPadding)
                }
            }
        }
    }
}

@Composable
fun WebSocketApp(innerPadding: PaddingValues) {
    val webSocketClient = remember { WebSocketClient() }
    val httpClient = remember { HttpClient() }
    val coroutineScope = rememberCoroutineScope()
    var latestPrice by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf("") }
    var trades by remember { mutableStateOf(emptyList<Trade>()) }
    var latestTradeId by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(Unit) {
        coroutineScope.launch {
            webSocketClient.connect()
            for (message in webSocketClient.getIncomingMessages()) {
                val fetchedTrades = httpClient.fetchBtcTrades()
                val newTrades = if (latestTradeId != null) {
                    fetchedTrades.filter { it.id > latestTradeId!! }
                } else {
                    fetchedTrades
                }
                if (newTrades.isNotEmpty()) {
                    trades = trades + newTrades
                    latestTradeId = newTrades.maxOfOrNull { it.id }
                }
                val markPriceUpdate = Json.decodeFromString<MarkPriceUpdate>(message)
                latestPrice = formatPrice(markPriceUpdate.price)
                symbol = markPriceUpdate.symbol
                timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
            }
        }
        onDispose {
            webSocketClient.close()
        }
    }

    Column(Modifier.padding(innerPadding)) {
        TickerCard(
            symbol = symbol,
            price = latestPrice,
            timestamp = timestamp,
            trades = trades
        )
    }
}

fun formatPrice(price: String): String {
    return try {
        val formattedPrice = DecimalFormat("#,##0.00").format(price.toDouble())
        "$$formattedPrice"
    } catch (e: NumberFormatException) {
        price
    }
}

@Composable
fun RowScope.TradeChart(trades: List<Trade>) {
    if (trades.isEmpty()) {
        Text(
            text = "No trade data available",
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    val prices = trades.map { it.price.toFloat() }

    val maxPrice = prices.maxOrNull() ?: 0f
    val minPrice = prices.minOrNull() ?: 0f
    val priceRange = if (maxPrice != minPrice) maxPrice - minPrice else 1f // Avoid zero range

    Column(
        modifier = Modifier.weight(1f)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp)
        ) {
            val path = Path()
            for (i in trades.indices) {
                val x = i.toFloat() / (trades.size - 1) * size.width // Use index for x-coordinates
                val y = size.height - ((prices[i] - minPrice) / priceRange) * size.height
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(path = path, color = Color.Yellow, style = Stroke(width = 3f))
        }
    }
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TickerCard(symbol: String, price: String, timestamp: String, trades: List<Trade>) {
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
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                AnimatedContent(
                    targetState = price,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 300)) with fadeOut(animationSpec = tween(durationMillis = 300))
                    },
                    label = "Price Animation"
                ) { targetPrice ->
                    Text(
                        text = targetPrice,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            TradeChart(trades = trades)
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f)
                    .align(Alignment.Bottom)
            ) {
                if (timestamp.isNotEmpty()) {
                    val localDateTime = try {
                        val instant = Instant.parse(timestamp)
                        instant.toLocalDateTime(TimeZone.currentSystemDefault())
                    } catch (e: Exception) {
                        LocalDateTime.parse(timestamp)
                    }
                    val date = "${localDateTime.dayOfMonth.toString().padStart(2, '0')}/${localDateTime.monthNumber.toString().padStart(2, '0')}/${localDateTime.year}"
                    val time = "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}:${localDateTime.second.toString().padStart(2, '0')}"

                    Column(
                        modifier = Modifier.align(Alignment.End),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TickerCard(
        symbol = "BTCUSDT",
        price = "34000.00",
        timestamp = "2024-07-27T18:36:32",
        trades = listOf(
            Trade(1, "68026.01000000", "0.00008000", "5.44208080", 1722110167963, true, true),
            Trade(2, "68026.00000000", "0.00028000", "19.04728000", 1722110167972, true, true),
            Trade(3, "68026.00000000", "0.00394000", "268.02244000", 1722110168037, true, true),
            Trade(4, "68026.00000000", "0.00178000", "121.08628000", 1722110168037, true, true),
            Trade(5, "68024.00000000", "0.00392000", "266.65408000", 1722110168037, true, true),
            Trade(6, "68024.00000000", "0.00208000", "141.48992000", 1722110168072, true, true),
            Trade(7, "68024.00000000", "0.00113000", "76.86712000", 1722110168072, true, true),
            Trade(8, "68024.00000000", "0.00321000", "218.35704000", 1722110168072, true, true),
            Trade(9, "68022.69000000", "0.00318000", "216.31215420", 1722110168072, true, true),
            Trade(10, "68022.69000000", "0.00012000", "8.16272280", 1722110168096, true, true),
            Trade(11, "68022.69000000", "0.00332000", "225.83533080", 1722110168098, true, true),
            Trade(12, "68022.38000000", "0.00332000", "225.83430160", 1722110168099, true, true)
        )
    )
}