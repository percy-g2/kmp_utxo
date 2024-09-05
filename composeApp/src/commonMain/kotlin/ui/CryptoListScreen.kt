package ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import model.CryptoPair
import model.UiKline
import theme.ThemeManager.store
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun CryptoList(
    cryptoViewModel: CryptoViewModel
) {
    var showDialog by remember { mutableStateOf(false) }
    val trades by cryptoViewModel.trades.collectAsState()
    val tickerDataMap by cryptoViewModel.tickerDataMap.collectAsState()
    val isLoading by cryptoViewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()
    val settings: Flow<Settings?> = store.updates

    val favPairs by settings.collectAsState(
        initial = Settings(
            selectedTheme = 0,
            favPairs = listOf(CryptoPair.BTCUSDT.symbol, CryptoPair.ETHUSDT.symbol, CryptoPair.SOLUSDT.symbol)
        )
    )

    if (showDialog) {
        CryptoPairDialog(
            onDismiss = {
                showDialog = false
            }
        )
    }

    LaunchedEffect(favPairs?.favPairs) {
        cryptoViewModel.reconnect()
    }

    Scaffold(
        floatingActionButton = {
            if (isLoading.not()) {
                ExtendedFloatingActionButton(
                    text = { Text(text = "Add Pair") },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "add-icon"
                        )
                    },
                    expanded = listState.isScrollingUp(),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    onClick = {
                        showDialog = true
                    }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState
                ) {
                    items(tickerDataMap.values.toList()) { tickerData ->
                        trades[tickerData.symbol]?.let { tradesList ->
                            TickerCard(
                                symbol = tickerData.symbol,
                                price = tickerData.lastPrice,
                                timestamp = tickerData.timestamp,
                                trades = tradesList
                            )
                        }
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

@Composable
fun CryptoPairDialog(
    onDismiss: () -> Unit
) {
    val settings = store.updates.collectAsState(null)
    val snackBarHostState = remember { SnackbarHostState() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Select Crypto Pair (${settings.value?.favPairs?.size}/10)") },
        text = {
            Box {
                LazyColumn {
                    items(CryptoPair.getAllPairs()) { pair ->
                        CryptoPairItem(
                            snackBarHostState = snackBarHostState,
                            pair = pair
                        )
                    }
                }
                SnackbarHost(
                    hostState = snackBarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun CryptoPairItem(
    snackBarHostState: SnackbarHostState,
    pair: String
) {
    val settings = store.updates.collectAsState(null)
    val coroutineScope = rememberCoroutineScope()
    val isAdded = settings.value?.favPairs?.contains(pair) ?: false
    val favPairs = settings.value?.favPairs ?: emptyList()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = pair)
        IconButton(onClick = {
            coroutineScope.launch {
                if (isAdded) {
                    store.update {
                        it?.copy(favPairs = favPairs.minus(pair))
                    }
                    snackBarHostState.showSnackbar("$pair removed!")
                } else {
                    if (favPairs.size < 10) {
                        store.update {
                            it?.copy(favPairs = favPairs.plus(pair))
                        }
                        snackBarHostState.showSnackbar("$pair added!")
                    } else {
                        snackBarHostState.showSnackbar("Max 10 can be added!")
                    }
                }
            }
        }) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (isAdded) MaterialTheme.colorScheme.onSurface else Color.Gray
            )
        }
    }
}

fun formatPrice(price: String): String = runCatching {
    val formattedPrice = price.toDouble().formatAsCurrency()
    "$$formattedPrice"
}.getOrElse {
    price
}

fun Double.formatAsCurrency(): String {
    val absValue = this.absoluteValue
    val integerPart = absValue.toInt()
    val fractionalPart = ((absValue - integerPart) * 100).roundToInt()

    val formattedInteger = integerPart.toString().reversed().chunked(3).joinToString(",").reversed()
    val formattedFractional = fractionalPart.toString().padStart(2, '0')

    return if (this < 0) "-$formattedInteger.$formattedFractional" else "$formattedInteger.$formattedFractional"
}

/**
 * Returns whether the lazy list is currently scrolling up.
 */
@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(value = firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(value = firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}