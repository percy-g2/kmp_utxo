package ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ktx.formatVolume
import model.SortParams
import model.TickerData
import model.UiKline
import theme.ThemeManager.store
import theme.greenDark
import theme.greenLight
import theme.redDark
import ui.components.LazyColumnScrollbar

@Composable
fun CryptoList(cryptoViewModel: CryptoViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val trades by cryptoViewModel.trades.collectAsState()
    val tickerDataMap by cryptoViewModel.filteredTickerDataMap.collectAsState()
    val isLoading by cryptoViewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()
    val tradingPairs = cryptoViewModel.tradingPairs.collectAsState()
    val settingsStore by store.updates.collectAsState(Settings())
    val coroutineScope = rememberCoroutineScope()

    val visibleSymbols by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val items = tickerDataMap.values.toList()

            val prefetchBuffer = 10
            val visibleIndices = layoutInfo.visibleItemsInfo.map { it.index }

            val startIndex = visibleIndices.minOrNull()?.minus(prefetchBuffer)?.coerceAtLeast(0) ?: 0
            val endIndex = visibleIndices.maxOrNull()?.plus(prefetchBuffer)?.coerceAtMost(items.lastIndex) ?: items.lastIndex

            (startIndex..endIndex).mapNotNull { index ->
                items.getOrNull(index)?.symbol
            }
        }
    }

    LaunchedEffect(visibleSymbols) {
        if (visibleSymbols.isNotEmpty()) {
            cryptoViewModel.fetchUiKlinesForVisibleSymbols(visibleSymbols)
        }
    }

    // Lifecycle observer to reconnect when the app comes back to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cryptoViewModel.reconnect()
                if (visibleSymbols.isNotEmpty()) {
                    cryptoViewModel.fetchUiKlinesForVisibleSymbols(visibleSymbols, true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                SearchBar(viewModel = cryptoViewModel)

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (tradingPairs.value.isEmpty()) {
                        items(6) {
                            ShimmerTradingPairItem()
                        }
                    } else {
                        items(tradingPairs.value) { symbol ->
                            TradingPairItem(
                                quote = symbol.quote,
                                isSelected = symbol.quote == settingsStore?.selectedTradingPair,
                                onClick = { quote ->
                                    cryptoViewModel.setSelectedTradingPair(quote)
                                    coroutineScope.launch {
                                        delay(100)
                                        listState.scrollToItem(0)
                                    }
                                }
                            )
                        }
                    }
                }

                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (tickerDataMap.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "No trading pairs found for ${settingsStore?.selectedTradingPair}",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (cryptoViewModel.searchQuery.value.isNotEmpty()) {
                                "Try adjusting your search criteria"
                            } else {
                                "Please wait while we fetch the latest data"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(state = listState) {
                            stickyHeader {
                                TickerCardListHeader(cryptoViewModel)
                            }
                            items(
                                items = tickerDataMap.values.toList(),
                                key = { it.symbol }
                            ) { tickerData ->
                                TickerCard(
                                    tickerData = tickerData,
                                    selectedTradingPair = settingsStore?.selectedTradingPair ?: "BTC",
                                    trades = trades[tickerData.symbol] ?: emptyList(),
                                    priceChangePercent = tickerData.priceChangePercent,
                                    cryptoViewModel = cryptoViewModel
                                )
                            }
                        }

                        LazyColumnScrollbar(listState = listState)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    viewModel: CryptoViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusManager = LocalFocusManager.current
    val settingsStore by store.updates.collectAsState(Settings())

    TextField(
        value = searchQuery,
        onValueChange = {
            viewModel.setSearchQuery(it)
        },
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        placeholder = { Text("Search ${settingsStore?.selectedTradingPair} pairs...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
            }
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun TradingPairItem(quote: String, isSelected: Boolean, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick(quote) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = quote,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ShimmerTradingPairItem() {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(width = 60.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .shimmerEffect()
    )
}

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.6f)
    )

    return this.background(
        brush = Brush.horizontalGradient(shimmerColors)
    )
}

@Composable
fun RowScope.TradeChart(
    trades: List<UiKline>,
    priceChangePercent: String
) {
    if (trades.isEmpty()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            LinearProgressIndicator()
        }
        return
    }

    val prices = remember(trades) { trades.map { it.closePrice.toFloat() } }
    val priceStats = remember(prices) {
        val max = prices.maxOrNull() ?: 0f
        val min = prices.minOrNull() ?: 0f
        val range = if (max != min) max - min else 1f
        Triple(min, max, range)
    }
    val (minPrice, _, priceRange) = priceStats

    val settingsState by store.updates.collectAsState(initial = Settings(appTheme = AppTheme.System))
    val isDarkTheme = (settingsState?.appTheme == AppTheme.Dark || (settingsState?.appTheme == AppTheme.System && isSystemInDarkTheme()))

    var chartSizeInPx by remember { mutableStateOf(Offset.Zero) }

    val priceChangeColor = when {
        priceChangePercent.toFloat() > 0f -> if (isDarkTheme) greenDark else greenLight
        priceChangePercent.toFloat() < 0f -> redDark
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .weight(1f)
            .onSizeChanged { size ->
                chartSizeInPx = Offset(size.width.toFloat(), size.height.toFloat())
            }
    ) {
        val points = remember(trades, chartSizeInPx, minPrice, priceRange) {
            calculatePoints(trades, chartSizeInPx, minPrice, priceRange)
        }
        Canvas(Modifier.fillMaxSize()) {
            if (chartSizeInPx.x <= 0 || chartSizeInPx.y <= 0) return@Canvas

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
        val x = index.toFloat() / (trades.size - 1).coerceAtLeast(1) * size.x
        val y = size.y - ((trade.closePrice.toFloat() - minPrice) / priceRange) * size.y
        Offset(x, y)
    }
}

@Composable
fun TickerCardListHeader(viewModel: CryptoViewModel) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f), horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .clickable {
                        viewModel.updateSortKey(SortParams.Pair)
                    },
            ) {
                TickerCardListHeaderItem("Name", SortParams.Pair, viewModel)
            }
            Text(
                text = "/ ",
                color = MaterialTheme.colorScheme.onSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.W300,
            )
            Row(
                Modifier
                    .clickable {
                        viewModel.updateSortKey(SortParams.Vol)

                    },
            ) {
                TickerCardListHeaderItem("Vol", SortParams.Vol, viewModel)
            }
        }
        Row(
            Modifier
                .clickable {
                    viewModel.updateSortKey(SortParams.Price)
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TickerCardListHeaderItem("Last Price", SortParams.Price, viewModel)
        }
        Row(
            Modifier
                .padding(start = 12.dp)
                .width(86.dp)
                .clickable {
                    viewModel.updateSortKey(SortParams.Change)
                },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TickerCardListHeaderItem("24h Chg%", SortParams.Change, viewModel)
        }
    }
}

@Composable
fun TickerCardListHeaderItem(
    text: String,
    sortKey: SortParams,
    viewModel: CryptoViewModel,
) {
    val currentSortKey = viewModel.currentSortKey
    val isSortDesc = viewModel.isSortDesc

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.W400,
            color = if (currentSortKey.value == sortKey) MaterialTheme.colorScheme.onBackground else Color.Gray,
            textAlign = TextAlign.End
        )
        Box {
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Change Up ${sortKey.name}",
                tint = if (currentSortKey.value == sortKey && isSortDesc.value) MaterialTheme.colorScheme.onBackground
                else Color.Gray,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .size(18.dp)
                    .rotate(180f)
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Change Down ${sortKey.name}",
                tint = if (viewModel.currentSortKey.value == sortKey && !isSortDesc.value) MaterialTheme.colorScheme.onBackground
                else Color.Gray,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
fun TickerCard(
    tickerData: TickerData,
    priceChangePercent: String,
    trades: List<UiKline>,
    selectedTradingPair: String,
    cryptoViewModel: CryptoViewModel,
) {
    val settingsState by store.updates.collectAsState(initial = Settings(appTheme = AppTheme.System))
    val isDarkTheme = (settingsState?.appTheme == AppTheme.Dark || (settingsState?.appTheme == AppTheme.System && isSystemInDarkTheme()))

    LaunchedEffect(tickerData.symbol) {
        cryptoViewModel.ensureChartData(tickerData.symbol)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(8.dp)
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
                if (tickerData.symbol.isNotEmpty()) {
                    val value = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontSize = 18.sp)) {
                            append(tickerData.symbol.replace(selectedTradingPair, ""))
                        }
                        withStyle(style = SpanStyle(fontSize = 14.sp, color = Color.Gray)) {
                            append("/$selectedTradingPair")
                        }
                    }
                    Text(
                        text = value,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                AnimatedContent(
                    targetState = tickerData.volume.formatVolume(),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "volume Animation"
                ) { targetVolume ->
                    Text(
                        text = targetVolume,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Gray,
                        modifier = Modifier.animateContentSize()
                    )
                }
            }
            TradeChart(trades = trades, priceChangePercent = priceChangePercent)
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.End),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedContent(
                        targetState = priceChangePercent,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "Price Change Percentage Animation"
                    ) { targetPriceChangePercent ->
                        val priceChangeColor = when {
                            targetPriceChangePercent.toFloat() > 0f -> if (isDarkTheme) greenDark else greenLight
                            targetPriceChangePercent.toFloat() < 0f -> redDark
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Text(
                            modifier = Modifier.animateContentSize().padding(bottom = 8.dp),
                            text = "$targetPriceChangePercent %",
                            color = priceChangeColor,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    AnimatedContent(
                        targetState = tickerData.lastPrice,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "Price Animation"
                    ) { targetPrice ->
                        Text(
                            text = targetPrice,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.animateContentSize().padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}