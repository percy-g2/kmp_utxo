package ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import ktx.buildStyledSymbol
import ktx.formatNewsDate
import ktx.formatPrice
import ktx.formatVolume
import logging.AppLogger
import model.NewsItem
import model.Ticker24hr
import model.TradingPair
import model.UiKline
import openLink
import org.jetbrains.compose.resources.stringResource
import theme.ThemeManager.store
import ui.components.LazyColumnScrollbar
import ui.utils.animatedShimmerEffect
import ui.utils.calculateChartPoints
import ui.utils.calculatePriceStats
import ui.utils.createPriceChangeGradientColors
import ui.utils.debouncedClickable
import ui.utils.getPriceChangeColor
import ui.utils.isDarkTheme
import ui.utils.limitKlinesForChart
import ui.utils.shimmerEffect
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.back
import utxo.composeapp.generated.resources.error
import utxo.composeapp.generated.resources.label_24h_change
import utxo.composeapp.generated.resources.label_24h_high
import utxo.composeapp.generated.resources.label_24h_low
import utxo.composeapp.generated.resources.label_24h_statistics
import utxo.composeapp.generated.resources.label_24h_volume_base
import utxo.composeapp.generated.resources.label_24h_volume_quote
import utxo.composeapp.generated.resources.label_best_ask
import utxo.composeapp.generated.resources.label_best_bid
import utxo.composeapp.generated.resources.label_last_price
import utxo.composeapp.generated.resources.label_last_quantity
import utxo.composeapp.generated.resources.label_open_price
import utxo.composeapp.generated.resources.label_previous_close
import utxo.composeapp.generated.resources.label_price_change
import utxo.composeapp.generated.resources.label_trading_information
import utxo.composeapp.generated.resources.label_volume
import utxo.composeapp.generated.resources.label_weighted_avg
import utxo.composeapp.generated.resources.latest_news
import utxo.composeapp.generated.resources.no_news_available
import utxo.composeapp.generated.resources.no_news_available_hint
import utxo.composeapp.generated.resources.no_news_providers_selected
import utxo.composeapp.generated.resources.no_news_providers_selected_hint
import utxo.composeapp.generated.resources.price_data_not_available
import utxo.composeapp.generated.resources.price_information
import utxo.composeapp.generated.resources.refresh
import utxo.composeapp.generated.resources.unknown_error
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun formatTickerUpdateTime(timestamp: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val systemTimeZone = TimeZone.currentSystemDefault()
        val localDateTime = instant.toLocalDateTime(systemTimeZone)
        
        val hour = localDateTime.hour
        val minute = localDateTime.minute
        
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        
        val minuteStr = if (minute < 10) "0$minute" else "$minute"
        
        "Updated: $displayHour:$minuteStr $amPm"
    } catch (e: Exception) {
        AppLogger.logger.e(throwable = e) { "Error formatting ticker update time" }
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    symbol: String,
    displaySymbol: String,
    onBackClick: () -> Unit,
    cryptoViewModel: CryptoViewModel,
    viewModel: CoinDetailViewModel = viewModel { CoinDetailViewModel() }
) {
    val settingsState by store.updates.collectAsState(initial = Settings(appTheme = AppTheme.System))
    val isDarkTheme = isDarkTheme(settingsState)
    val state by viewModel.state.collectAsState()
    val tradingPairs by cryptoViewModel.tradingPairs.collectAsState()
    
    // Get enabled providers from settings - allow empty set (no providers selected)
    // If settings don't have enabledRssProviders field (old settings), default to all enabled
    val enabledProviders = settingsState?.enabledRssProviders ?: model.RssProvider.DEFAULT_ENABLED_PROVIDERS
    
    // Convert Set to a stable, sorted string key for LaunchedEffect dependency
    // Use "empty" as key when no providers are selected
    val enabledProvidersKey = if (enabledProviders.isEmpty()) {
        "empty"
    } else {
        enabledProviders.sorted().joinToString(",")
    }
    
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val selectedTimeframe = state.selectedTimeframe

    // Reload when symbol or enabled providers change
    LaunchedEffect(symbol, enabledProvidersKey) {
        AppLogger.logger.d { "CoinDetailScreen: LaunchedEffect triggered - symbol: $symbol, providers: $enabledProviders, key: $enabledProvidersKey" }
        // Always clear cache first to ensure we fetch fresh data with correct providers
        coroutineScope.launch {
            viewModel.clearCache()
        }
        // Use a local copy to ensure we're using the correct providers
        val providersToUse = enabledProviders.toSet()
        AppLogger.logger.d { "CoinDetailScreen: About to call loadCoinData with providers: $providersToUse" }
        viewModel.loadCoinData(symbol, providersToUse)
    }
    
    // Clean up WebSocket when screen leaves composition
    DisposableEffect(symbol) {
        onDispose {
            AppLogger.logger.d { "CoinDetailScreen: Disposing WebSocket connections for $symbol" }
            // WebSocket cleanup is handled by ViewModel.onCleared()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Column {
                        Text(displaySymbol.buildStyledSymbol())
                        state.ticker?.closeTime?.let { timestamp ->
                            Text(
                                text = formatTickerUpdateTime(timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        AppLogger.logger.d { "CoinDetailScreen: Manual refresh for $symbol with providers: $enabledProviders" }
                        viewModel.refresh(symbol, enabledProviders)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.refresh)
                        )
                    }
                },
                windowInsets = WindowInsets(
                    top = 0.dp,
                    bottom = 0.dp
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(top = paddingValues.calculateTopPadding()))
        ) {

            when {
                state.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.error),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.error ?: stringResource(Res.string.unknown_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Timeframe Selection Buttons
                            item {
                                TimeframeSelector(
                                    selectedTimeframe = selectedTimeframe,
                                    onTimeframeSelected = { timeframe ->
                                        viewModel.changeTimeframe(timeframe)
                                    }
                                )
                            }
                            
                            // Chart Section - Show shimmer if loading, otherwise show chart
                            item {
                                if (state.isLoadingChart) {
                                    ShimmerChartPlaceholder()
                                } else {
                                    CoinDetailChart(
                                        klines = state.klines,
                                        priceChangePercent = state.ticker?.priceChangePercent
                                            ?: "0",
                                        isDarkTheme = isDarkTheme,
                                        symbol = symbol,
                                        tradingPairs = tradingPairs,
                                        onTooltipVisibilityChange = { visible ->
                                            viewModel.setTooltipVisible(visible)
                                        }
                                    )
                                }
                            }

                            // Price Info Section - Show shimmer if loading, otherwise show price info
                            item {
                                if (state.isLoadingTicker) {
                                    ShimmerPriceInfoPlaceholder()
                                } else {
                                    PriceInfoSection(
                                        symbol = symbol,
                                        ticker = state.ticker,
                                        tradingPairs = tradingPairs
                                    )
                                }
                            }

                            // Order Book Heat Map Section
                            item {
                                OrderBookHeatMap(
                                    orderBookData = state.orderBookData,
                                    orderBookError = state.orderBookError,
                                    symbol = symbol,
                                    tradingPairs = tradingPairs,
                                    isDarkTheme = isDarkTheme
                                )
                            }

                            // News Section Header
                            item {
                                Text(
                                    text = stringResource(Res.string.latest_news),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            // News Items - Show shimmer placeholders for pending providers, show items as they arrive
                            val hasNoProviders = enabledProviders.isEmpty()
                            if (hasNoProviders) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Article,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .padding(bottom = 16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.6f
                                                )
                                            )
                                            Text(
                                                text = stringResource(Res.string.no_news_providers_selected),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = stringResource(Res.string.no_news_providers_selected_hint),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.7f
                                                ),
                                                modifier = Modifier.padding(top = 8.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Show news items as they arrive
                                items(state.news) { newsItem ->
                                    NewsItemCard(
                                        newsItem = newsItem,
                                        isDarkTheme = isDarkTheme
                                    )
                                }

                                // Show shimmer placeholders for providers that are still loading
                                if (state.loadingNewsProviders.isNotEmpty()) {
                                    items(count = state.loadingNewsProviders.size, key = { it }) {
                                        ShimmerNewsItemPlaceholder()
                                    }
                                }

                                // Show empty state only if no news and no providers loading
                                if (state.news.isEmpty()
                                    && state.loadingNewsProviders.isEmpty()
                                    && !state.isLoadingNews
                                ) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Article,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .padding(bottom = 16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.6f
                                                    )
                                                )
                                                Text(
                                                    text = stringResource(Res.string.no_news_available),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = stringResource(Res.string.no_news_available_hint),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.7f
                                                    ),
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        LazyColumnScrollbar(listState = listState)
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalTime::class)
@Composable
fun CoinDetailChart(
    klines: List<UiKline>,
    priceChangePercent: String,
    isDarkTheme: Boolean,
    symbol: String,
    tradingPairs: List<TradingPair>,
    onTooltipVisibilityChange: (Boolean) -> Unit = {}
) {
    // If no klines data, show empty state (loading is handled by shimmer placeholder)
    if (klines.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chart data not available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val priceChangeFloat = remember(priceChangePercent) {
        priceChangePercent.toFloatOrNull() ?: 0f
    }

    val prices = remember(klines) {
        val limitedKlines = limitKlinesForChart(klines)
        ArrayList<Float>(limitedKlines.size).apply {
            limitedKlines.forEach { kline ->
                add(kline.closePrice.toFloatOrNull() ?: 0f)
            }
        }
    }

    val (minPrice, _, priceRange) = remember(prices) {
        calculatePriceStats(prices)
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    val priceChangeColor = remember(priceChangeFloat, isDarkTheme, primaryColor) {
        getPriceChangeColor(priceChangeFloat, isDarkTheme, primaryColor)
    }

    val gradientColors = remember(priceChangeColor) {
        createPriceChangeGradientColors(priceChangeColor)
    }

    var chartSizeInPx by remember { mutableStateOf(Offset.Zero) }
    var tooltipPosition by remember { mutableStateOf<Offset?>(null) }
    var chartPointPosition by remember { mutableStateOf<Offset?>(null) }
    var tooltipPrice by remember { mutableStateOf<String?>(null) }
    var tooltipTime by remember { mutableStateOf<String?>(null) }
    var showTooltip by remember { mutableStateOf(false) }
    var hideTooltipTrigger by remember { mutableStateOf(0L) }

    // Notify ViewModel when tooltip visibility changes
    LaunchedEffect(showTooltip) {
        onTooltipVisibilityChange(showTooltip)
    }
    
    // Hide tooltip after 5 seconds
    LaunchedEffect(showTooltip, hideTooltipTrigger) {
        if (showTooltip && tooltipPosition != null) {
            delay(5000)
            if (showTooltip) {
                showTooltip = false
                tooltipPosition = null
                chartPointPosition = null
                tooltipPrice = null
                tooltipTime = null
            }
        }
    }

    // Format timestamp to readable time
    fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null) return ""
        return try {
            val instant = Instant.fromEpochMilliseconds(timestamp)
            val systemTimeZone = TimeZone.currentSystemDefault()
            val localDateTime = instant.toLocalDateTime(systemTimeZone)
            
            val monthNames = listOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )
            
            val month = monthNames[localDateTime.month.number - 1]
            val day = localDateTime.day
            val hour = localDateTime.hour
            val minute = localDateTime.minute
            
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            
            val minuteStr = if (minute < 10) "0$minute" else "$minute"
            
            "$month $day, $displayHour:$minuteStr $amPm"
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "Error formatting timestamp" }
            ""
        }
    }

    // Find the closest kline data point and its chart position for a given x position
    fun findClosestKlineAndPosition(xPosition: Float): Pair<UiKline?, Offset?> {
        if (klines.isEmpty() || chartSizeInPx.x <= 0 || priceRange <= 0) return Pair(null, null)
        
        val limitedKlines = limitKlinesForChart(klines)
        
        val normalizedX = (xPosition / chartSizeInPx.x).coerceIn(0f, 1f)
        val index = (normalizedX * (limitedKlines.size - 1)).toInt().coerceIn(0, limitedKlines.lastIndex)
        val kline = limitedKlines[index]

        val price = kline.closePrice.toFloatOrNull() ?: minPrice
        val x = normalizedX * chartSizeInPx.x
        val y = chartSizeInPx.y - ((price - minPrice) / priceRange) * chartSizeInPx.y
        return Pair(kline, Offset(x, y))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 64.dp, start = 16.dp, bottom = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .height(240.dp)
                .onSizeChanged { size ->
                    // Only update if size actually changed to minimize recompositions
                    val newSize = Offset(size.width.toFloat(), size.height.toFloat())
                    if (chartSizeInPx != newSize) {
                        chartSizeInPx = newSize
                    }
                }
                .pointerInput(klines, chartSizeInPx, minPrice, priceRange) {
                    detectTapGestures { tapOffset ->
                        val (closestKline, chartPoint) = findClosestKlineAndPosition(tapOffset.x)
                        if (closestKline != null && chartPoint != null) {
                            tooltipPosition = tapOffset
                            chartPointPosition = chartPoint
                            tooltipPrice = closestKline.closePrice.formatPrice(symbol, tradingPairs)
                            tooltipTime = formatTimestamp(closestKline.closeTime ?: closestKline.openTime)
                            showTooltip = true
                            hideTooltipTrigger = Clock.System.now().toEpochMilliseconds()
                        }
                    }
                }
                .pointerInput(klines, chartSizeInPx, minPrice, priceRange) {
                    detectDragGestures(
                        onDragEnd = {
                            // Trigger hide tooltip after 5 seconds
                            hideTooltipTrigger = Clock.System.now().toEpochMilliseconds()
                        }
                    ) { change, _ ->
                        val dragOffset = change.position
                        val (closestKline, chartPoint) = findClosestKlineAndPosition(dragOffset.x)
                        if (closestKline != null && chartPoint != null) {
                            tooltipPosition = dragOffset
                            chartPointPosition = chartPoint
                            tooltipPrice = closestKline.closePrice.formatPrice(symbol, tradingPairs)
                            tooltipTime = formatTimestamp(closestKline.closeTime ?: closestKline.openTime)
                            showTooltip = true
                            hideTooltipTrigger = Clock.System.now().toEpochMilliseconds()
                        }
                    }
                }
        ) {
            val points = remember(klines, chartSizeInPx, minPrice, priceRange) {
                if (chartSizeInPx.x <= 0 || chartSizeInPx.y <= 0 || klines.isEmpty()) {
                    emptyList()
                } else {
                    calculateChartPoints(klines, chartSizeInPx, minPrice, priceRange)
                }
            }

            val linePath = remember(points) {
                if (points.isEmpty()) {
                    null
                } else {
                    Path().apply {
                        val firstPoint = points[0]
                        moveTo(firstPoint.x, firstPoint.y)
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
                        for (point in points) {
                            lineTo(point.x, point.y)
                        }
                        lineTo(chartSizeInPx.x, chartSizeInPx.y)
                        close()
                    }
                }
            }

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

                drawPath(path = fillPath, brush = gradientBrush)
                drawPath(
                    path = linePath,
                    color = priceChangeColor,
                    style = Stroke(width = 3f)
                )

                // Draw indicator dot on chart line at touch point
                chartPointPosition?.let { point ->
                    // Draw a circle at the touch point on the line
                    drawCircle(
                        color = priceChangeColor,
                        radius = 6.dp.toPx(),
                        center = point
                    )
                    // Draw a white border around the circle
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = point,
                        style = Stroke(width = 2f)
                    )
                }
            }

            // Tooltip - positioned next to the chart point
            if (showTooltip) {
                val density = LocalDensity.current
                chartPointPosition?.let { chartPoint ->
                    tooltipPrice?.let { price ->
                        val paddingPx = with(density) { 16.dp.toPx() }
                        val estimatedTooltipWidthPx = with(density) { 160.dp.toPx() }
                        val tooltipHeightPx = with(density) { if (tooltipTime?.isNotEmpty() == true) 56.dp.toPx() else 36.dp.toPx() }
                        val minMarginPx = with(density) { 8.dp.toPx() }
                        
                        // Determine best side: right if point is on left half, left if on right half
                        val spaceOnRight = chartSizeInPx.x - chartPoint.x
                        val spaceOnLeft = chartPoint.x
                        val useRightSide = spaceOnRight >= estimatedTooltipWidthPx + paddingPx || spaceOnRight > spaceOnLeft
                        
                        val tooltipXPx = if (useRightSide) {
                            // Position to the right of the point
                            (chartPoint.x + paddingPx).coerceIn(minMarginPx, chartSizeInPx.x - estimatedTooltipWidthPx - minMarginPx)
                        } else {
                            // Position to the left of the point
                            (chartPoint.x - estimatedTooltipWidthPx - paddingPx).coerceIn(minMarginPx, chartSizeInPx.x - estimatedTooltipWidthPx)
                        }
                        
                        // Position vertically centered with the chart point, but keep within bounds
                        val tooltipYPx = (chartPoint.y - tooltipHeightPx / 2).coerceIn(minMarginPx, chartSizeInPx.y - tooltipHeightPx - minMarginPx)

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = with(density) { tooltipXPx.toDp() }, y = with(density) { tooltipYPx.toDp() })
                        ) {
                            Card(
                                elevation = CardDefaults.cardElevation(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = price,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    tooltipTime?.takeIf { it.isNotEmpty() }?.let { time ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = time,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun PriceInfoSection(
    symbol: String,
    ticker: Ticker24hr?,
    tradingPairs: List<TradingPair> = emptyList()
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            // Header with expand/collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .debouncedClickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.price_information),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            if (ticker != null) {
                // Always show essential info
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    PriceRow(
                        label = stringResource(Res.string.label_last_price),
                        value = ticker.lastPrice.formatPrice(symbol, tradingPairs),
                        isHighlighted = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val priceChangePercent = ticker.priceChangePercent.toDoubleOrNull() ?: 0.0
                    val priceChangeColor = when {
                        priceChangePercent > 0 -> Color(0xFF4CAF50) // Green
                        priceChangePercent < 0 -> Color(0xFFF44336) // Red
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    PriceRow(
                        stringResource(Res.string.label_24h_change),
                        "${if (priceChangePercent >= 0) "+" else ""}${ticker.priceChangePercent}%",
                        valueColor = priceChangeColor
                    )
                }

                // Expanded details
                if (isExpanded) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PriceRow(
                            label = stringResource(Res.string.label_price_change),
                            value = ticker.priceChange.formatPrice(symbol, tradingPairs)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 24h Statistics Section
                        Text(
                            text = stringResource(Res.string.label_24h_statistics),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        PriceRow(
                            label = stringResource(Res.string.label_open_price),
                            value = ticker.openPrice.formatPrice(symbol, tradingPairs)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PriceRow(
                            label = stringResource(Res.string.label_previous_close),
                            value = ticker.prevClosePrice.formatPrice(symbol, tradingPairs)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PriceRow(
                            label = stringResource(Res.string.label_24h_high),
                            value = ticker.highPrice.formatPrice(symbol, tradingPairs)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PriceRow(
                            label = stringResource(Res.string.label_24h_low),
                            value = ticker.lowPrice.formatPrice(symbol, tradingPairs)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PriceRow(
                            label = stringResource(Res.string.label_weighted_avg),
                            value = ticker.weightedAvgPrice.formatPrice(symbol, tradingPairs)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Trading Info Section
                        Text(
                            text = stringResource(Res.string.label_trading_information),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        PriceRow(
                            label = stringResource(Res.string.label_best_bid),
                            value = "${
                                ticker.bidPrice.formatPrice(
                                    symbol,
                                    tradingPairs
                                )
                            } (${ticker.bidQty})"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PriceRow(
                            label = stringResource(Res.string.label_best_ask),
                            value = "${
                                ticker.askPrice.formatPrice(
                                    symbol,
                                    tradingPairs
                                )
                            } (${ticker.askQty})"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PriceRow(
                            label = stringResource(Res.string.label_last_quantity),
                            value = ticker.lastQty
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Volume Section
                        Text(
                            text = stringResource(Res.string.label_volume),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        PriceRow(
                            label = stringResource(Res.string.label_24h_volume_quote),
                            value = ticker.quoteVolume.formatVolume()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PriceRow(
                            stringResource(
                                resource = Res.string.label_24h_volume_base
                            ),
                            value = ticker.volume.formatVolume()
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.price_data_not_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PriceRow(
    label: String,
    value: String,
    isHighlighted: Boolean = false,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isHighlighted) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun NewsItemCard(
    newsItem: NewsItem,
    isDarkTheme: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .debouncedClickable {
                if (newsItem.link.isNotEmpty()) {
                    try {
                        openLink(newsItem.link)
                    } catch (e: Exception) {
                        AppLogger.logger.e(throwable = e) { "Failed to open link: ${newsItem.link}" }
                    }
                }
            },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = newsItem.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDarkTheme) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .background(
                            if (isDarkTheme) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = newsItem.pubDate.formatNewsDate(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = newsItem.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (newsItem.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = newsItem.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


@Composable
fun ShimmerChartPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .animatedShimmerEffect()
        )
    }
}

@Composable
fun ShimmerPriceInfoPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Price rows shimmer
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ShimmerNewsItemPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Source and date shimmer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Title shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Description shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
    }
}

@Composable
fun TimeframeSelector(
    selectedTimeframe: String,
    onTimeframeSelected: (String) -> Unit
) {
    val timeframes = listOf("1m", "5m", "15m", "1h", "4h", "1d")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            timeframes.forEach { timeframe ->
                val isSelected = timeframe == selectedTimeframe
                Box(
                    modifier = Modifier
                        .debouncedClickable(
                            debounceMillis = 500L,
                            haptic = true
                        ) {
                            onTimeframeSelected(timeframe)
                        }
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = timeframe,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}