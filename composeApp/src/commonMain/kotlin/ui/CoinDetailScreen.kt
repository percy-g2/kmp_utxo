package ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import copyToClipboard
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ktx.buildStyledSymbol
import ktx.formatNewsDate
import ktx.formatPrice
import ktx.formatVolume
import logging.AppLogger
import model.NewsItem
import model.Ticker24hr
import model.TradingPair
import network.AiInsightService
import openLink
import org.jetbrains.compose.resources.stringResource
import ui.components.LazyColumnScrollbar
import ui.components.ScrollToEdgeButton
import ui.components.TradingViewChart
import ui.utils.debouncedClickable
import ui.utils.getPriceChangeColor
import ui.utils.isDarkTheme
import ui.utils.shimmerEffect
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.ai_insights
import utxo.composeapp.generated.resources.ai_insights_copied
import utxo.composeapp.generated.resources.ai_insights_copy
import utxo.composeapp.generated.resources.ai_insights_disclaimer
import utxo.composeapp.generated.resources.ai_insights_error
import utxo.composeapp.generated.resources.ai_insights_loading
import utxo.composeapp.generated.resources.ai_insights_rate_limited
import utxo.composeapp.generated.resources.ai_insights_rate_limited_stale
import utxo.composeapp.generated.resources.ai_insights_retry
import utxo.composeapp.generated.resources.portfolio_open_settings
import utxo.composeapp.generated.resources.back
import utxo.composeapp.generated.resources.chat_ask_anything
import utxo.composeapp.generated.resources.chat_open
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
import utxo.composeapp.generated.resources.news_partial_failure
import utxo.composeapp.generated.resources.news_retry
import utxo.composeapp.generated.resources.news_unavailable
import utxo.composeapp.generated.resources.news_unavailable_hint
import utxo.composeapp.generated.resources.no_news_available
import utxo.composeapp.generated.resources.no_news_available_hint
import utxo.composeapp.generated.resources.no_news_providers_selected
import utxo.composeapp.generated.resources.no_news_providers_selected_hint
import utxo.composeapp.generated.resources.price_data_not_available
import utxo.composeapp.generated.resources.price_information
import utxo.composeapp.generated.resources.refresh
import utxo.composeapp.generated.resources.unknown_error
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
    viewModel: CoinDetailViewModel = viewModel { CoinDetailViewModel() },
    /** Navigates to Settings so a rate-limited user can add an llm7.io token without hunting for it. */
    onOpenSettings: (() -> Unit)? = null,
    /**
     * Opens the per-coin AI chat, optionally with a question the user picked from a chip. Null
     * where this screen can't reach the chat, which hides the whole section rather than dead-ending.
     */
    onAskAi: ((String?) -> Unit)? = null
) {
    val settingsState by SettingsStore.settings.collectAsState()
    val isDarkTheme = isDarkTheme(settingsState)
    val state by viewModel.state.collectAsState()
    val tradingPairs by cryptoViewModel.tradingPairs.collectAsState()
    val baseAsset = remember(symbol) { AiInsightService.extractBaseAsset(symbol) }
    
    // Get enabled providers from settings - allow empty set (no providers selected)
    // If settings don't have enabledRssProviders field (old settings), default to all enabled
    val enabledProviders = settingsState?.enabledRssProviders ?: model.RssProvider.DEFAULT_ENABLED_PROVIDERS

    // Optional llm7.io token that raises the AI rate limits; blank = anonymous, which still works.
    val aiApiToken = settingsState?.aiApiToken ?: ""

    // null settings means "not read from disk yet" (see SettingsStore), NOT "defaults". Loading on
    // that placeholder made every coin fetch its ticker and news twice: once against the placeholder
    // providers and again when the real settings landed.
    val settingsLoaded = settingsState != null

    // Convert Set to a stable, sorted string key for LaunchedEffect dependency
    // Use "empty" as key when no providers are selected
    val enabledProvidersKey = if (enabledProviders.isEmpty()) {
        "empty"
    } else {
        enabledProviders.sorted().joinToString(",")
    }
    
    val listState = rememberLazyListState()
    val selectedTimeframe = state.selectedTimeframe

    // Reload when the symbol or the enabled providers change — but never before settings are read.
    LaunchedEffect(symbol, enabledProvidersKey, settingsLoaded) {
        if (!settingsLoaded) return@LaunchedEffect
        AppLogger.logger.d { "CoinDetailScreen: LaunchedEffect triggered - symbol: $symbol, providers: $enabledProviders, key: $enabledProvidersKey" }
        // Use a local copy to ensure we're using the correct providers
        val providersToUse = enabledProviders.toSet()
        AppLogger.logger.d { "CoinDetailScreen: About to call loadCoinData with providers: $providersToUse" }
        viewModel.loadCoinData(symbol, providersToUse, aiApiToken)
    }

    // The token can change while this screen is alive — on the iOS 26 native tab bar this screen is
    // never torn down while Settings is edited. Regenerate the insight, don't reload everything.
    LaunchedEffect(aiApiToken, settingsLoaded) {
        if (settingsLoaded) viewModel.updateApiToken(aiApiToken)
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
                        viewModel.refresh(symbol, enabledProviders, aiApiToken)
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
                            
                            // Chart Section - TradingView widget
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    TradingViewChart(
                                        symbol = symbol,
                                        interval = selectedTimeframe,
                                        isDarkTheme = isDarkTheme,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(400.dp)
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
                                        isDarkTheme = isDarkTheme,
                                        tradingPairs = tradingPairs
                                    )
                                }
                            }

                            // AI Insights Section - market overview generated from 24h ticker data
                            item {
                                AiInsightCard(
                                    insight = state.aiInsight,
                                    isLoading = state.isLoadingInsight,
                                    rateLimited = state.insightRateLimited,
                                    error = state.insightError,
                                    ticker = state.ticker,
                                    baseAsset = baseAsset,
                                    hasNews = state.news.isNotEmpty(),
                                    onRetry = { viewModel.regenerateInsight() },
                                    onOpenSettings = onOpenSettings,
                                    onAskAi = onAskAi
                                )
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
                                // Show news items as they arrive. Stable key (link) so Compose moves
                                // existing cards across the re-sort on each provider append instead of
                                // rebuilding every NewsItemCard (re-parsing dates, re-laying-out text).
                                items(state.news, key = { it.link }) { newsItem ->
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

                                // Nothing conclusive to show until every provider has reported.
                                val newsSettled =
                                    state.loadingNewsProviders.isEmpty() && !state.isLoadingNews
                                val failedCount = state.failedNewsProviders.size
                                // Only claim everything failed when everything actually did. One
                                // dead feed alongside seven that were read fine and simply had
                                // nothing about this coin is an empty result, not an outage, and
                                // telling the user to check their connection would send them after
                                // a problem they do not have.
                                val allFailed =
                                    failedCount > 0 && failedCount >= enabledProviders.size

                                if (newsSettled && allFailed) {
                                    // Every source we asked was unreachable. That is a broken
                                    // transport, not a quiet news day, and the two used to render
                                    // identically — which is exactly how a total CORS outage on the
                                    // web build stayed invisible. Say so, and offer a way out.
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
                                                    imageVector = Icons.Default.CloudOff,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .padding(bottom = 16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.6f
                                                    )
                                                )
                                                Text(
                                                    text = stringResource(Res.string.news_unavailable),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = stringResource(Res.string.news_unavailable_hint),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.7f
                                                    ),
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                                TextButton(
                                                    onClick = {
                                                        viewModel.refresh(symbol, enabledProviders, aiApiToken)
                                                    },
                                                    modifier = Modifier.padding(top = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(text = stringResource(Res.string.news_retry))
                                                }
                                            }
                                        }
                                    }
                                } else if (newsSettled && state.news.isEmpty()) {
                                    // Every feed was read fine; none of them mentioned this coin.
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

                                // Some sources dropped out while others reported. Whether that left
                                // a partial list or nothing at all, what is on screen is real but
                                // incomplete, so say so rather than passing it off as the lot.
                                if (newsSettled && !allFailed && failedCount > 0) {
                                    item {
                                        Text(
                                            text = stringResource(
                                                Res.string.news_partial_failure,
                                                failedCount,
                                                enabledProviders.size
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.7f
                                            ),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                        LazyColumnScrollbar(listState = listState)
                        // Gate the totalItemsCount read behind derivedStateOf so scrolling only
                        // re-triggers this button when the item COUNT changes, not every scroll frame.
                        val scrollTotal by remember(listState) {
                            derivedStateOf { listState.layoutInfo.totalItemsCount }
                        }
                        ScrollToEdgeButton(
                            listState = listState,
                            totalItems = scrollTotal
                        )

                        if (onAskAi != null) {
                            AskAiFab(
                                baseAsset = baseAsset,
                                onClick = { onAskAi(null) }
                            )
                        }
                    }
                }
            }
        }
    }

}


/** How many chips the card offers before handing over to the chat screen's fuller set. */
private const val CARD_SUGGESTION_COUNT = 3

/**
 * Clears [ScrollToEdgeButton]'s slot: that is a 40dp small FAB pinned 16dp from the bottom of the
 * same Box, so this sits one row above it.
 */
private val AskAiFabBottomInset = 68.dp

/**
 * Always-available way into the chat.
 *
 * The AI card's chips are the richer entry — they arrive with a question already chosen — but they
 * scroll away, and this screen is long. Somebody reading the order book or the news shouldn't have
 * to scroll back up to ask something.
 */
@Composable
private fun BoxScope.AskAiFab(baseAsset: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 24.dp, bottom = AskAiFabBottomInset),
        // `primary`, not `primaryContainer`: this palette is a muted monochrome where the container
        // tone (#33342E in dark) is within a hair of the background, which left the FAB reading as
        // a second scroll button. This also matches the chat's own send button.
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            // The sparkle is this app's AI mark (it heads the insights card), so the label is what
            // carries the meaning for anyone who can't see the icon.
            contentDescription = stringResource(Res.string.chat_open, baseAsset)
        )
    }
}

@Composable
fun AiInsightCard(
    insight: String?,
    isLoading: Boolean,
    rateLimited: Boolean,
    error: String?,
    ticker: Ticker24hr?,
    baseAsset: String,
    hasNews: Boolean,
    onRetry: () -> Unit,
    /** null where this screen can't reach Settings, which hides the shortcut rather than dead-ending. */
    onOpenSettings: (() -> Unit)? = null,
    /** null where this screen can't reach the chat, which hides the section rather than dead-ending. */
    onAskAi: ((String?) -> Unit)? = null
) {
    val hasTicker = ticker != null

    // Treat the pre-ticker window as loading so the card never shows an empty body.
    val showLoading = isLoading ||
        (!hasTicker && error == null && !rateLimited && insight == null)

    // Keyed on the text so a regenerated overview always starts from the un-copied state. A tick
    // counter rather than a Boolean, because writing `true` over `true` is a structural-equality
    // no-op — the effect would keep its old key and a repeat tap wouldn't restart the window.
    var copyTick by remember(insight) { mutableStateOf(0) }
    val copied = copyTick > 0
    LaunchedEffect(copyTick) {
        if (copyTick > 0) {
            delay(2000)
            copyTick = 0
        }
    }

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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.ai_insights),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status lives in a live region rather than in the button's accessible name:
                    // a label swap on an already-focused control isn't announced, and the name
                    // should keep describing the action, not report a past event.
                    if (copied) {
                        Text(
                            text = stringResource(Res.string.ai_insights_copied),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    // Mirrors the `when` branch below that renders the text: an error takes over the
                    // body even while a previous overview is retained, and copying what isn't on
                    // screen would be a lie.
                    if (!showLoading && error == null && insight != null) {
                        IconButton(
                            onClick = {
                                copyToClipboard(insight)
                                copyTick++
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = stringResource(Res.string.ai_insights_copy),
                                tint = if (copied) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    if (!showLoading && (insight != null || error != null)) {
                        IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(Res.string.ai_insights_retry),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                showLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(Res.string.ai_insights_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                error != null -> {
                    Text(
                        text = stringResource(Res.string.ai_insights_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(stringResource(Res.string.ai_insights_retry))
                    }
                }

                // Ahead of [rateLimited] on purpose: an overview already on screen is worth more
                // than the limit notice, so a throttled Retry demotes the limit to a footnote
                // rather than replacing what the user was reading.
                insight != null -> {
                    // Selectable so users can lift a single line out instead of the whole overview.
                    SelectionContainer {
                        Text(
                            text = insight,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (rateLimited) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.ai_insights_rate_limited_stale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.ai_insights_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                rateLimited -> {
                    Text(
                        text = stringResource(Res.string.ai_insights_rate_limited),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(stringResource(Res.string.ai_insights_retry))
                        }
                        // Only offered where Settings is reachable from here (see AiInsightCard).
                        if (onOpenSettings != null) {
                            TextButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(stringResource(Res.string.portfolio_open_settings))
                            }
                        }
                    }
                }
            }

            // Deliberately outside the `when` above: the chat is useful whatever the overview did.
            // Hiding it while the overview loads or after it was throttled would take the feature
            // away exactly when the user most wants to ask something.
            if (onAskAi != null) {
                AskAiSection(
                    baseAsset = baseAsset,
                    ticker = ticker,
                    hasNews = hasNews,
                    hasOverview = insight != null,
                    onAskAi = onAskAi
                )
            }
        }
    }
}

/**
 * Entry point into the per-coin chat: a few of the same chips the chat screen would show, so a
 * question is one tap away, plus a route in for anything else.
 *
 * Chips carry their own label as the question, so what the user tapped is exactly what lands in
 * their transcript.
 */
@Composable
private fun AskAiSection(
    baseAsset: String,
    ticker: Ticker24hr?,
    hasNews: Boolean,
    hasOverview: Boolean,
    onAskAi: (String?) -> Unit
) {
    // The ticker here is fed by a WebSocket, so it changes several times a second. Deriving the
    // chips from the live value would re-rank the row — and re-word a chip that quotes the day's
    // move — continuously under the user's thumb. Snapshot it, refreshed only when the move
    // crosses a tenth of a percent.
    val moveKey = changeMagnitudeLabel(ticker)
    val isDown = (ticker?.priceChangePercent?.toDoubleOrNull() ?: 0.0) < 0
    val stableTicker = remember(moveKey, isDown, ticker != null) { ticker }

    val suggestions = remember(baseAsset, stableTicker, hasNews, hasOverview) {
        suggestionsFor(
            baseAsset = baseAsset,
            ticker = stableTicker,
            hasNews = hasNews,
            hasOverview = hasOverview,
            isFirstTurn = true,
            max = CARD_SUGGESTION_COUNT
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.chat_open, baseAsset),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = { onAskAi(null) }) {
            Text(
                text = stringResource(Res.string.chat_ask_anything),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = suggestions, key = { it.name }) { suggestion ->
            val label = suggestionText(suggestion, baseAsset, stableTicker)
            SuggestionChip(
                onClick = { onAskAi(label) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun PriceInfoSection(
    symbol: String,
    ticker: Ticker24hr?,
    isDarkTheme: Boolean,
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
                    val priceChangeColor = getPriceChangeColor(
                        ticker.priceChangePercent,
                        isDarkTheme,
                        MaterialTheme.colorScheme.onSurface
                    )

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
    onTimeframeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeframes = remember { listOf("1m", "5m", "15m", "1h", "4h", "1d") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(
                items = timeframes,
                key = { it }
            ) { timeframe ->
                TimeframeChip(
                    timeframe = timeframe,
                    isSelected = timeframe == selectedTimeframe,
                    onClick = { onTimeframeSelected(timeframe) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeframeChip(
    timeframe: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = timeframe,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        },
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else null
    )
}