package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ktx.buildStyledSymbol
import ktx.formatNewsDate
import ktx.formatPrice
import ktx.formatVolume
import model.NewsItem
import model.Ticker24hr
import model.TradingPair
import openLink
import org.jetbrains.compose.resources.stringResource
import theme.ThemeManager.store
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.back
import utxo.composeapp.generated.resources.current_price
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
import utxo.composeapp.generated.resources.price_data_not_available
import utxo.composeapp.generated.resources.price_information
import utxo.composeapp.generated.resources.refresh
import utxo.composeapp.generated.resources.unknown_error

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
    val isDarkTheme =
        (settingsState?.appTheme == AppTheme.Dark || (settingsState?.appTheme == AppTheme.System && isSystemInDarkTheme()))
    val state by viewModel.state.collectAsState()
    val tradingPairs by cryptoViewModel.tradingPairs.collectAsState()

    LaunchedEffect(symbol) {
        viewModel.loadCoinData(symbol)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = { Text(displaySymbol.buildStyledSymbol()) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(symbol) }) {
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(top = paddingValues.calculateTopPadding()))
        ) {

            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Price Info Section
                        item {
                            PriceInfoSection(
                                symbol = symbol,
                                ticker = state.ticker,
                                tradingPairs = tradingPairs
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

                        // News Items
                        if (state.news.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(Res.string.no_news_available),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(state.news) { newsItem ->
                                NewsItemCard(
                                    newsItem = newsItem,
                                    isDarkTheme = isDarkTheme
                                )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.price_information),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (ticker != null) {
                // Current Price Section
                Text(
                    text = stringResource(Res.string.current_price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

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
                Spacer(modifier = Modifier.height(4.dp))
                PriceRow(
                    label = stringResource(Res.string.label_price_change),
                    value = ticker.priceChange.formatPrice(symbol, tradingPairs)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 24h Statistics Section
                Text(
                    text = stringResource(Res.string.label_24h_statistics),
                    style = MaterialTheme.typography.titleMedium,
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
                    style = MaterialTheme.typography.titleMedium,
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
                    style = MaterialTheme.typography.titleMedium,
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

            } else {
                Text(
                    text = stringResource(Res.string.price_data_not_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            .clickable {
                if (newsItem.link.isNotEmpty()) {
                    try {
                        openLink(newsItem.link)
                    } catch (e: Exception) {
                        println("Failed to open link: $newsItem.link - ${e.message}")
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