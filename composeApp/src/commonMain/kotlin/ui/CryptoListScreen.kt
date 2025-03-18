package ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ktx.formatPair
import model.TickerDataInfo
import model.UiKline
import theme.ThemeManager.store
import theme.greenDark
import theme.greenLight
import theme.redDark
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun CryptoList(cryptoViewModel: CryptoViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDialog by remember { mutableStateOf(false) }
    val trades by cryptoViewModel.trades.collectAsState()
    val tickerDataMap by cryptoViewModel.tickerDataMap.collectAsState()
    val isLoading by cryptoViewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()
    val settings: Flow<Settings?> = store.updates
    val symbols = cryptoViewModel.symbols.collectAsState()

    val favPairs by settings.collectAsState(initial = Settings())

    if (showDialog) {
        CryptoPairDialog(
            symbols = symbols.value.sortedWith(
                compareByDescending { it.symbol in (favPairs?.favPairs ?: emptyList()) }
            ),
            onDismiss = { showDialog = false }
        )
    }

    DisposableEffect(lifecycleOwner, favPairs) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cryptoViewModel.reconnect()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                    onClick = { showDialog = true }
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
                LazyColumn(state = listState) {
                    items(
                        items = tickerDataMap.values.toList(),
                        key = { it.symbol }
                    ) { tickerData ->
                        trades[tickerData.symbol]?.let { tradesList ->
                            TickerCard(
                                symbol = tickerData.symbol,
                                price = tickerData.lastPrice,
                                timestamp = tickerData.timestamp,
                                trades = tradesList,
                                priceChangePercent = tickerData.priceChangePercent
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
    trades: List<UiKline>,
    priceChangePercent: String
) {
    if (trades.isEmpty()) {
        Text(
            text = "No trade data available",
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    val prices = remember(trades) { trades.map { it.closePrice.toFloat() } }
    val maxPrice by remember(prices) { derivedStateOf { prices.maxOrNull() ?: 0f } }
    val minPrice by remember(prices) { derivedStateOf { prices.minOrNull() ?: 0f } }
    val priceRange by remember(maxPrice, minPrice) { derivedStateOf { if (maxPrice != minPrice) maxPrice - minPrice else 1f } }
    val selectedTheme by store.updates.collectAsState(initial = Settings(selectedTheme = Theme.SYSTEM.id))
    val isDarkTheme = (selectedTheme?.selectedTheme == Theme.DARK.id
        || (selectedTheme?.selectedTheme == Theme.SYSTEM.id && isSystemInDarkTheme()))
    var chartSize by remember { mutableStateOf(Offset.Zero) }
    val priceChangeColor = when {
        priceChangePercent.toFloat() > 0f -> if (isDarkTheme) greenDark else greenLight
        else -> redDark
    }

    Box(Modifier.weight(1f)) {
        Canvas(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
            chartSize = Offset(size.width, size.height)
            val points = calculatePoints(trades, chartSize, minPrice, priceRange)

            // Create the main path for the line
            val linePath = Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y)
                    else lineTo(point.x, point.y)
                }
            }

            // Create a path for the gradient fill
            val fillPath = Path().apply {
                // Start from the bottom-left corner
                moveTo(0f, size.height)

                // Add all points from the line path
                points.forEach { point ->
                    lineTo(point.x, point.y)
                }

                // Close the path by going to the bottom-right corner
                lineTo(size.width, size.height)
                close()
            }

            // Determine gradient colors based on price change
            val gradientColors = when {
                priceChangePercent.toFloat() > 0f -> {
                    // Green gradient for positive change
                    listOf(
                        priceChangeColor.copy(alpha = 0.6f),
                        priceChangeColor.copy(alpha = 0.3f),
                        priceChangeColor.copy(alpha = 0.1f),
                        priceChangeColor.copy(alpha = 0f)
                    )
                }
                else -> {
                    // Red gradient for negative change
                    listOf(
                        priceChangeColor.copy(alpha = 0.6f),
                        priceChangeColor.copy(alpha = 0.3f),
                        priceChangeColor.copy(alpha = 0.1f),
                        priceChangeColor.copy(alpha = 0f)
                    )
                }
            }

            // Draw the gradient fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = size.height
                )
            )

            // Draw the line on top
            drawPath(
                path = linePath,
                color = priceChangeColor,
                style = Stroke(width = 3f)
            )
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
    priceChangePercent: String,
    trades: List<UiKline>
) {
    val selectedTheme by store.updates.collectAsState(initial = Settings(selectedTheme = Theme.SYSTEM.id))
    val isDarkTheme = (selectedTheme?.selectedTheme == Theme.DARK.id
        || (selectedTheme?.selectedTheme == Theme.SYSTEM.id && isSystemInDarkTheme()))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .weight(1f)
            ) {
                if (symbol.isNotEmpty()) {
                    val parts = symbol.formatPair().split("/")
                    val value = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontSize = 18.sp)) {
                            append(parts[0]) // First part (e.g., BTC, ETH)
                        }
                        withStyle(style = SpanStyle(fontSize = 14.sp)) {
                            append("/${parts[1]}") // Second part (e.g., /USDT)
                        }
                    }
                    Text(
                        text = value,
                      //  style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                AnimatedContent(
                    targetState = priceChangePercent,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "Price Change Percentage Animation"
                ) { targetPriceChangePercent ->
                    val priceChangeColor = when {
                        priceChangePercent.toFloat() > 0f -> if (isDarkTheme) greenDark else greenLight
                        else -> redDark
                    }
                    Text(
                        modifier = Modifier.padding(bottom = 8.dp),
                        text = "$targetPriceChangePercent %",
                        color = priceChangeColor,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                AnimatedContent(
                    targetState = price,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "Price Animation"
                ) { targetPrice ->
                    Text(
                        text = targetPrice,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.animateContentSize()
                    )
                }
            }
            TradeChart(trades = trades, priceChangePercent)
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .weight(1f)
            ) {
                if (timestamp.isNotEmpty()) {
                    val localDateTime = try {
                        Instant.parse(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
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
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.End),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        AnimatedContent(
                            targetState = time,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
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
    symbols: List<TickerDataInfo>,
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
                    items(symbols) { pair ->
                        CryptoPairItem(
                            snackBarHostState = snackBarHostState,
                            pair = pair.symbol
                        )
                    }
                }
            }
        },
        dismissButton = { SnackbarHost(hostState = snackBarHostState) },
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

    val onClick: () -> Unit = {
        coroutineScope.launch {
            if (isAdded) {
                store.update { it?.copy(favPairs = favPairs.minus(pair)) }
                snackBarHostState.showSnackbar("$pair removed!")
            } else {
                if (favPairs.size < 10) {
                    store.update { it?.copy(favPairs = favPairs.plus(pair)) }
                    snackBarHostState.showSnackbar("$pair added!")
                } else {
                    snackBarHostState.showSnackbar("Max 10 can be added!")
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = pair.formatPair()
        )
        IconButton(onClick = onClick) {
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
    var previousFirstVisibleItem by remember { mutableStateOf(0) }
    var previousFirstVisibleItemOffset by remember { mutableStateOf(0) }
    var previousScrollDirection by remember { mutableStateOf(true) }

    return remember {
        derivedStateOf {
            val firstVisibleItemIndex = firstVisibleItemIndex
            val firstVisibleItemOffset = firstVisibleItemScrollOffset

            val scrollingUp = when {
                firstVisibleItemIndex < previousFirstVisibleItem -> true
                firstVisibleItemIndex > previousFirstVisibleItem -> false
                firstVisibleItemOffset < previousFirstVisibleItemOffset -> true
                firstVisibleItemOffset > previousFirstVisibleItemOffset -> false
                else -> previousScrollDirection
            }

            previousFirstVisibleItem = firstVisibleItemIndex
            previousFirstVisibleItemOffset = firstVisibleItemOffset
            previousScrollDirection = scrollingUp

            scrollingUp
        }
    }.value
}