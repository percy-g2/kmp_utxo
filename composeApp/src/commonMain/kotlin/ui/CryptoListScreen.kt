package ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ktx.formatVolume
import model.SortParams
import model.TickerData
import model.UiKline
import org.jetbrains.compose.resources.stringResource
import theme.ThemeManager.store
import theme.yellowDark
import theme.yellowLight
import ui.components.CryptoIcon
import ui.components.LazyColumnScrollbar
import ui.utils.calculateChartPoints
import ui.utils.calculatePriceStats
import ui.utils.createPriceChangeGradientColors
import ui.utils.getPriceChangeColor
import ui.utils.isDarkTheme
import ui.utils.limitKlinesForChart
import ui.utils.shimmerEffect
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.clear_search
import utxo.composeapp.generated.resources.favorite
import utxo.composeapp.generated.resources.favorites_title
import utxo.composeapp.generated.resources.header_24h_chg
import utxo.composeapp.generated.resources.header_last_price
import utxo.composeapp.generated.resources.header_name
import utxo.composeapp.generated.resources.header_slash
import utxo.composeapp.generated.resources.header_vol
import utxo.composeapp.generated.resources.no_favorites_yet
import utxo.composeapp.generated.resources.no_trading_pairs_found
import utxo.composeapp.generated.resources.please_wait_fetching
import utxo.composeapp.generated.resources.search
import utxo.composeapp.generated.resources.search_placeholder
import utxo.composeapp.generated.resources.sort_change_down
import utxo.composeapp.generated.resources.sort_change_up
import utxo.composeapp.generated.resources.tap_star_to_add
import utxo.composeapp.generated.resources.try_adjusting_search
import utxo.composeapp.generated.resources.unfavorite
import kotlin.math.abs

// Stable composition local for settings to avoid recompositions
val LocalSettings = staticCompositionLocalOf<Settings?> { null }

@Composable
fun CryptoList(
    cryptoViewModel: CryptoViewModel,
    onCoinClick: (String, String) -> Unit = { _, _ -> }
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Collect state once at top level - hoist to minimize recompositions
    val trades by cryptoViewModel.trades.collectAsState()
    val tickerDataMap by cryptoViewModel.filteredTickerDataMap.collectAsState()
    val isLoading by cryptoViewModel.isLoading.collectAsState()
    val tradingPairs by cryptoViewModel.tradingPairs.collectAsState()
    val settingsStore by store.updates.collectAsState(initial = null)
    
    // Derive stable list of ticker items to avoid recreating list on every recomposition
    val tickerItems by derivedStateOf { tickerDataMap.values.toList() }
    
    // Derive stable trading pair list
    val tradingPairList by derivedStateOf { tradingPairs.map { it.quote } }
    
    val currentTradingPair by derivedStateOf { settingsStore?.selectedTradingPair ?: "BTC" }

    // Optimize visible symbols calculation - use derivedStateOf for automatic recomposition only when needed
    val visibleSymbols by derivedStateOf {
        val layoutInfo = listState.layoutInfo
        if (tickerItems.isEmpty() || layoutInfo.visibleItemsInfo.isEmpty()) {
            return@derivedStateOf emptyList()
        }

        val prefetchBuffer = 10
        val visibleIndices = layoutInfo.visibleItemsInfo.map { it.index }

        val startIndex = visibleIndices.minOrNull()
            ?.minus(prefetchBuffer)
            ?.coerceAtLeast(0)
            ?: 0

        val endIndex = visibleIndices.maxOrNull()
            ?.plus(prefetchBuffer)
            ?.coerceAtMost(tickerItems.lastIndex)
            ?: tickerItems.lastIndex

        (startIndex..endIndex).mapNotNull { index ->
            tickerItems.getOrNull(index)?.symbol
        }
    }

    // Track fetch job for cancellation
    var fetchJob by remember { mutableStateOf<Job?>(null) }
    
    // Lifecycle-aware data fetching with cancellation
    // Only load charts for symbols that don't have them yet (normal behavior)
    LaunchedEffect(visibleSymbols, lifecycleOwner.lifecycle.currentState) {
        val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (visibleSymbols.isNotEmpty() && isResumed) {
            // Cancel previous job if still running
            fetchJob?.cancel()
            fetchJob = coroutineScope.launch(Dispatchers.Default) {
                try {
                    // Only load charts for symbols that don't have them (no forceRefresh)
                    cryptoViewModel.fetchUiKlinesForVisibleSymbols(visibleSymbols, forceRefresh = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Silently handle errors during cancellation
                }
            }
        }
    }
    
    // Lifecycle observer with proper cleanup - cancel all work when view disappears
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Only resume work when view is actually visible
                    coroutineScope.launch {
                        cryptoViewModel.reconnect()
                        if (visibleSymbols.isNotEmpty()) {
                            fetchJob?.cancel()
                            fetchJob = launch(Dispatchers.Default) {
                                try {
                                    cryptoViewModel.fetchUiKlinesForVisibleSymbols(visibleSymbols, forceRefresh = true)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    // Handle errors silently
                                }
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    // Cancel all background work when view is not visible
                    fetchJob?.cancel()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Cancel all jobs on dispose
            fetchJob?.cancel()
        }
    }
    
    // Expose callback to trigger chart refresh when returning from detail
    Scaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                SearchBar(viewModel = cryptoViewModel)

                val lazyRowState = rememberLazyListState()
                LazyRow(
                    state = lazyRowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (tradingPairs.isEmpty()) {
                        items(count = 6, key = { it }) { _ ->
                            ShimmerTradingPairItem()
                        }
                    } else {
                        items(
                            items = tradingPairs,
                            key = { it.quote } // Stable key for efficient recomposition
                        ) { symbol ->
                            TradingPairItem(
                                quote = symbol.quote,
                                isSelected = symbol.quote == currentTradingPair,
                                onClick = { quote ->
                                    cryptoViewModel.setSelectedTradingPair(quote)
                                    coroutineScope.launch {
                                        delay(150) // Slightly longer delay for smoother transition
                                        listState.animateScrollToItem(0)
                                        val selectedIndex = tradingPairList.indexOf(quote)
                                        if (selectedIndex != -1) {
                                            lazyRowState.animateScrollToItem(selectedIndex)
                                        }
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
                            text = stringResource(
                                Res.string.no_trading_pairs_found,
                                currentTradingPair
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        val searchQuery by cryptoViewModel.searchQuery.collectAsState()
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                stringResource(Res.string.try_adjusting_search)
                            } else {
                                stringResource(Res.string.please_wait_fetching)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(tradingPairList, currentTradingPair) {
                                detectHorizontalDragGestures(
                                    onDragEnd = { /* No-op */ },
                                    onDragCancel = { /* No-op */ },
                                    onHorizontalDrag = { _, dragAmount ->
                                        val currentIndex = tradingPairList.indexOf(currentTradingPair)
                                        // We'll use a threshold for a "swipe" action, ignore small moves
                                        val threshold = 80f // pixels

                                        if (abs(dragAmount) < threshold) return@detectHorizontalDragGestures

                                        if (currentIndex != -1 && tradingPairList.isNotEmpty()) {
                                            val nextIndex = when {
                                                dragAmount > threshold -> (currentIndex - 1).coerceAtLeast(0)
                                                dragAmount < -threshold -> (currentIndex + 1).coerceAtMost(tradingPairList.lastIndex)
                                                else -> currentIndex
                                            }
                                            if (nextIndex != currentIndex) {
                                                cryptoViewModel.setSelectedTradingPair(tradingPairList[nextIndex])
                                                coroutineScope.launch {
                                                    delay(150) // Match the delay with manual selection
                                                    listState.animateScrollToItem(0)
                                                    lazyRowState.animateScrollToItem(nextIndex)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        if (settingsStore != null) {
                            // Provide settings via composition local to avoid prop drilling
                            CompositionLocalProvider(LocalSettings provides settingsStore) {
                                AnimatedContent(
                                    targetState = currentTradingPair,
                                    transitionSpec = {
                                        val currentIndex = tradingPairList.indexOf(initialState)
                                        val targetIndex = tradingPairList.indexOf(targetState)
                                        val isNext = targetIndex > currentIndex

                                        slideInHorizontally(
                                            initialOffsetX = { fullWidth -> if (isNext) fullWidth else -fullWidth },
                                            animationSpec = tween(300)
                                        ) + fadeIn(animationSpec = tween(300)) togetherWith
                                                slideOutHorizontally(
                                                    targetOffsetX = { fullWidth -> if (isNext) -fullWidth else fullWidth },
                                                    animationSpec = tween(300)
                                                ) + fadeOut(animationSpec = tween(300))
                                    },
                                    label = "Trading Pair Content Animation"
                                ) { tradingPair ->
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        stickyHeader {
                                            TickerCardListHeader(cryptoViewModel)
                                        }
                                        // Add spacer only once at the start
                                        item(key = "spacer_top") {
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        items(
                                            items = tickerItems,
                                            key = { it.symbol } // Stable key - critical for performance
                                        ) { tickerData ->
                                            TickerCard(
                                                tickerData = tickerData,
                                                selectedTradingPair = tradingPair,
                                                trades = trades[tickerData.symbol] ?: emptyList(),
                                                priceChangePercent = tickerData.priceChangePercent,
                                                tradingPairs = tradingPairs,
                                                cryptoViewModel = cryptoViewModel,
                                                onClick = onCoinClick
                                            )
                                        }
                                    }

                                    LazyColumnScrollbar(listState = listState)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                            }
                        }
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
    val settingsStore by store.updates.collectAsState(initial = null)

    TextField(
        value = searchQuery,
        onValueChange = {
            viewModel.setSearchQuery(it)
        },
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        placeholder = {
            Text(
                stringResource(
                    Res.string.search_placeholder,
                    settingsStore?.selectedTradingPair ?: "BTC"
                )
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(Res.string.search)
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.clear_search)
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
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = tween(200),
        label = "Trading Pair Scale Animation"
    )

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
            .animateContentSize(animationSpec = tween(200))
            .let {
                if (isSelected) it.graphicsLayer(scaleX = scale, scaleY = scale)
                else it
            }
    ) {
        AnimatedContent(
            targetState = isSelected,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "Trading Pair Text Animation"
        ) { selected ->
            Text(
                modifier = Modifier.padding(horizontal = 2.dp),
                text = quote,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
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

    // Use composition local for settings to avoid recomposition
    val settingsState = LocalSettings.current
    val isDarkTheme = isDarkTheme(settingsState)
    
    // Get MaterialTheme color outside remember block (composable context required)
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // Parse price change once and cache
    val priceChangeFloat = remember(priceChangePercent) {
        priceChangePercent.toFloatOrNull() ?: 0f
    }
    
    // Calculate prices off main thread and cache - limit to reduce memory
    val prices = remember(trades) {
        val limitedTrades = limitKlinesForChart(trades)
        // Pre-allocate list to avoid reallocations
        ArrayList<Float>(limitedTrades.size).apply {
            limitedTrades.forEach { trade ->
                add(trade.closePrice.toFloatOrNull() ?: 0f)
            }
        }
    }
    
    // Calculate price stats once and cache
    val (minPrice, _, priceRange) = remember(prices) {
        calculatePriceStats(prices)
    }

    // Cache price change color calculation
    val priceChangeColor = remember(priceChangeFloat, isDarkTheme, primaryColor) {
        getPriceChangeColor(priceChangeFloat, isDarkTheme, primaryColor)
    }
    
    // Cache gradient colors to avoid recreating list every frame
    val gradientColors = remember(priceChangeColor) {
        createPriceChangeGradientColors(priceChangeColor)
    }

    var chartSizeInPx by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .weight(1f)
            .onSizeChanged { size ->
                // Only update if size actually changed to minimize recompositions
                val newSize = Offset(size.width.toFloat(), size.height.toFloat())
                if (chartSizeInPx != newSize) {
                    chartSizeInPx = newSize
                }
            }
    ) {
        // Pre-calculate points off main thread and cache
        val points = remember(trades, chartSizeInPx, minPrice, priceRange) {
            if (chartSizeInPx.x <= 0 || chartSizeInPx.y <= 0 || trades.isEmpty()) {
                emptyList()
            } else {
                calculateChartPoints(trades, chartSizeInPx, minPrice, priceRange)
            }
        }
        
        // Pre-calculate paths efficiently - reuse Path objects when possible
        val linePath = remember(points) {
            if (points.isEmpty()) {
                null
            } else {
                // Create path efficiently - avoid intermediate allocations
                Path().apply {
                    val firstPoint = points[0]
                    moveTo(firstPoint.x, firstPoint.y)
                    // Use for loop instead of forEachIndexed to reduce allocations
                    for (i in 1 until points.size) {
                        val point = points[i]
                        lineTo(point.x, point.y)
                    }
                }
            }
        }
        
        val fillPath = remember(points, chartSizeInPx) {
            if (points.isEmpty() || chartSizeInPx.x <= 0 || chartSizeInPx.y <= 0) {
                null
            } else {
                Path().apply {
                    moveTo(0f, chartSizeInPx.y)
                    // Use for loop to reduce allocations
                    for (point in points) {
                        lineTo(point.x, point.y)
                    }
                    lineTo(chartSizeInPx.x, chartSizeInPx.y)
                    close()
                }
            }
        }
        
        // Cache brush to avoid recreation every frame
        val gradientBrush = remember(gradientColors, chartSizeInPx) {
            if (chartSizeInPx.y > 0) {
                Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = chartSizeInPx.y
                )
            } else {
                null
            }
        }
        
        Canvas(Modifier.fillMaxSize()) {
            if (chartSizeInPx.x <= 0 || chartSizeInPx.y <= 0 || linePath == null || fillPath == null || gradientBrush == null) {
                return@Canvas
            }

            // Draw the gradient fill using cached brush
            drawPath(
                path = fillPath,
                brush = gradientBrush
            )

            // Draw the line on top - use cached path
            drawPath(
                path = linePath,
                color = priceChangeColor,
                style = Stroke(width = 3f)
            )
        }
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
                    TickerCardListHeaderItem(
                        stringResource(
                            Res.string.header_name
                        ),
                        SortParams.Pair, viewModel
                    )
                }
                Text(
                    text = stringResource(Res.string.header_slash),
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
                    TickerCardListHeaderItem(
                        text = stringResource(Res.string.header_vol),
                        sortKey = SortParams.Vol,
                        viewModel = viewModel
                    )
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
            TickerCardListHeaderItem(
                text = stringResource(
                    resource = Res.string.header_last_price
                ),
                sortKey = SortParams.Price,
                viewModel = viewModel
            )
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
            TickerCardListHeaderItem(
                text = stringResource(
                    resource = Res.string.header_24h_chg
                ),
                sortKey = SortParams.Change,
                viewModel = viewModel
            )
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
                contentDescription = stringResource(
                    Res.string.sort_change_up,
                    sortKey.name
                ),
                tint = if (currentSortKey.value == sortKey && isSortDesc.value)
                    MaterialTheme.colorScheme.onBackground
                else Color.Gray,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .size(18.dp)
                    .rotate(180f)
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(
                    Res.string.sort_change_down, sortKey.name
                ),
                tint = if (viewModel.currentSortKey.value == sortKey && !isSortDesc.value)
                    MaterialTheme.colorScheme.onBackground
                else Color.Gray,
                modifier = Modifier.padding(top = 6.dp).size(18.dp)
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
    tradingPairs: List<model.TradingPair>,
    cryptoViewModel: CryptoViewModel,
    onClick: (String, String) -> Unit = { _, _ -> }
) {
    // Use composition local for settings to avoid recomposition
    val settingsState = LocalSettings.current
    val isDarkTheme = isDarkTheme(settingsState)
    
    // Calculate actual trading pair once and cache - no state collection needed
    val actualTradingPair = remember(tickerData.symbol, tradingPairs, selectedTradingPair) {
        tradingPairs
            .filter { pair -> tickerData.symbol.endsWith(pair.quote) }
            .maxByOrNull { it.quote.length }
            ?.quote ?: selectedTradingPair
    }
    
    // Pre-compute symbol display text to avoid string operations during composition
    val symbolDisplayText = remember(tickerData.symbol, actualTradingPair) {
        buildAnnotatedString {
            withStyle(style = SpanStyle(fontSize = 18.sp)) {
                append(tickerData.symbol.replace(actualTradingPair, ""))
            }
            withStyle(style = SpanStyle(fontSize = 14.sp, color = Color.Gray)) {
                append("/$actualTradingPair")
            }
        }
    }
    
    // Pre-compute formatted volume
    val formattedVolume = remember(tickerData.volume) {
        tickerData.volume.formatVolume()
    }

    // --- Bounce animation magic on first appearance! ---
    val appeared = remember(tickerData.symbol) { mutableStateOf(false) }
    LaunchedEffect(tickerData.symbol) {
        appeared.value = true
    }
    val bounceScale by animateFloatAsState(
        targetValue = if (appeared.value) 1f else 0.95f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = 0.42f // subtle bounce
        ),
        label = "TickerCardBounceScale"
    )
    // --- End bounce animation ---

    // Lifecycle-aware chart data fetching with cancellation
    LaunchedEffect(tickerData.symbol) {
        try {
            cryptoViewModel.ensureChartData(tickerData.symbol)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Silently handle errors
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(
                scaleX = bounceScale,
                scaleY = bounceScale
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable { onClick(tickerData.symbol, symbolDisplayText.text) },
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(8.dp),
            ) {
                // Crypto Icon
                CryptoIcon(
                    symbol = tickerData.symbol,
                    tradingPair = actualTradingPair,
                    modifier = Modifier.padding(end = 12.dp),
                    size = 40.dp
                )
                
                Column(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .weight(1f)
                ) {
                    if (tickerData.symbol.isNotEmpty()) {
                        Text(
                            text = symbolDisplayText,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnimatedContent(
                            targetState = formattedVolume,
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
                        Spacer(modifier = Modifier.width(4.dp))
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
                            val priceChangeColor = getPriceChangeColor(
                                targetPriceChangePercent,
                                isDarkTheme,
                                MaterialTheme.colorScheme.primary
                            )
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

        val isFavorite = remember(settingsState, tickerData.symbol) {
            settingsState?.favPairs?.contains(tickerData.symbol) == true
        }
        IconButton(
            onClick = {
                if (isFavorite) cryptoViewModel.removeFromFavorites(tickerData.symbol)
                else cryptoViewModel.addToFavorites(tickerData.symbol)
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 0.dp, y = (-4).dp)
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                )
                .clip(CircleShape)
        ) {
            Icon(
                modifier = Modifier.padding(4.dp),
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (isFavorite)
                    stringResource(Res.string.unfavorite)
                else stringResource(Res.string.favorite),
                tint = if (isFavorite) if (isDarkTheme) yellowDark else yellowLight else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun FavoritesListScreen(
    cryptoViewModel: CryptoViewModel,
    onCoinClick: (String, String) -> Unit = { _, _ -> }
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val trades by cryptoViewModel.trades.collectAsState()
    val tickerDataMap by cryptoViewModel.favoritesTickerDataMap.collectAsState()
    val isLoading by cryptoViewModel.isLoading.collectAsState()
    val tradingPairs by cryptoViewModel.tradingPairs.collectAsState()
    val listState = rememberLazyListState()
    val settingsStore by store.updates.collectAsState(initial = null)
    
    // Derive stable list to avoid recreating on every recomposition
    val tickerItems by derivedStateOf { tickerDataMap.values.toList() }
    val currentTradingPair by derivedStateOf { settingsStore?.selectedTradingPair ?: "BTC" }
    
    // Track fetch job for cancellation
    var fetchJob by remember { mutableStateOf<Job?>(null) }
    
    // Calculate visible symbols for lifecycle-aware fetching
    val visibleSymbols by derivedStateOf {
        val layoutInfo = listState.layoutInfo
        if (tickerItems.isEmpty() || layoutInfo.visibleItemsInfo.isEmpty()) {
            return@derivedStateOf emptyList()
        }
        val prefetchBuffer = 10
        val visibleIndices = layoutInfo.visibleItemsInfo.map { it.index }
        val startIndex = visibleIndices.minOrNull()?.minus(prefetchBuffer)?.coerceAtLeast(0) ?: 0
        val endIndex = visibleIndices.maxOrNull()?.plus(prefetchBuffer)?.coerceAtMost(tickerItems.lastIndex) ?: tickerItems.lastIndex
        (startIndex..endIndex).mapNotNull { index -> tickerItems.getOrNull(index)?.symbol }
    }
    
    // Lifecycle-aware data fetching with cancellation
    // Also trigger when tickerItems change to ensure charts load even if trades were cleared
    LaunchedEffect(visibleSymbols, lifecycleOwner.lifecycle.currentState, tickerItems.size) {
        val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (visibleSymbols.isNotEmpty() && isResumed) {
            fetchJob?.cancel()
            fetchJob = coroutineScope.launch(Dispatchers.Default) {
                try {
                    // Force refresh to ensure charts load even if trades were cleared
                    cryptoViewModel.fetchUiKlinesForVisibleSymbols(visibleSymbols, forceRefresh = true)
                } catch (_: CancellationException) {
                    // Silently handle errors during cancellation
                } catch (_: Exception) {
                    // Silently handle errors during cancellation
                }
            }
        } else if (tickerItems.isNotEmpty() && isResumed && visibleSymbols.isEmpty()) {
            // If we have favorites but visibleSymbols is empty (list not laid out yet),
            // fetch data for all favorites to ensure charts load
            fetchJob?.cancel()
            fetchJob = coroutineScope.launch(Dispatchers.Default) {
                try {
                    val allFavoriteSymbols = tickerItems.map { it.symbol }
                    cryptoViewModel.fetchUiKlinesForVisibleSymbols(allFavoriteSymbols, forceRefresh = true)
                } catch (_: CancellationException) {
                    // Silently handle errors during cancellation
                } catch (_: Exception) {
                    // Silently handle errors during cancellation
                }
            }
        }
    }
    
    // Lifecycle observer with proper cleanup and resume handling
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // When resuming, fetch prices and chart data for all favorites
                    if (tickerItems.isNotEmpty()) {
                        val allFavoriteSymbols = tickerItems.map { it.symbol }
                        
                        // First, fetch current prices for favorites that don't have real data yet
                        cryptoViewModel.fetchFavoritesPrices(allFavoriteSymbols)
                        
                        // Then fetch chart data
                        fetchJob?.cancel()
                        fetchJob = coroutineScope.launch(Dispatchers.Default) {
                            try {
                                cryptoViewModel.fetchUiKlinesForVisibleSymbols(allFavoriteSymbols, forceRefresh = true)
                            } catch (_: CancellationException) {
                                // Silently handle errors during cancellation
                            } catch (_: Exception) {
                                // Silently handle errors
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    fetchJob?.cancel()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            fetchJob?.cancel()
        }
    }
    
    // Also fetch prices when favorites list changes (e.g., when navigating to favorites screen)
    LaunchedEffect(tickerItems.size) {
        if (tickerItems.isNotEmpty()) {
            val allFavoriteSymbols = tickerItems.map { it.symbol }
            cryptoViewModel.fetchFavoritesPrices(allFavoriteSymbols)
        }
    }

    Scaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                Text(
                    text = stringResource(resource = Res.string.favorites_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
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
                            imageVector = Icons.Default.StarBorder,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = stringResource(resource = Res.string.no_favorites_yet),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(resource = Res.string.tap_star_to_add),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    CompositionLocalProvider(LocalSettings provides settingsStore) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(state = listState) {
                                items(
                                    items = tickerItems,
                                    key = { it.symbol } // Stable key for efficient recomposition
                                ) { tickerData ->
                                    if (tickerItems.indexOf(tickerData) == 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    TickerCard(
                                        tickerData = tickerData,
                                        selectedTradingPair = currentTradingPair,
                                        trades = trades[tickerData.symbol] ?: emptyList(),
                                        priceChangePercent = tickerData.priceChangePercent,
                                        tradingPairs = tradingPairs,
                                        cryptoViewModel = cryptoViewModel,
                                        onClick = onCoinClick
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
}