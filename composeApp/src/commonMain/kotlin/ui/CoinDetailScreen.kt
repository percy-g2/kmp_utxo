package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import ktx.formatVolume
import model.NewsItem
import model.Ticker24hr
import openLink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    symbol: String,
    onBackClick: () -> Unit,
    viewModel: CoinDetailViewModel = viewModel { CoinDetailViewModel() }
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(symbol) {
        viewModel.loadCoinData(symbol)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = { Text(symbol) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(symbol) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                windowInsets = WindowInsets(
                    top = 0.dp,
                    bottom = 0.dp
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {

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
                                text = "Error",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.error ?: "Unknown error",
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
                                    ticker = state.ticker
                                )
                            }

                            // News Section Header
                            item {
                                Text(
                                    text = "Latest News",
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
                                            text = "No news available for this coin",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(state.news) { newsItem ->
                                    NewsItemCard(newsItem = newsItem)
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
    ticker: Ticker24hr?
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
                text = "Price Information",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (ticker != null) {
                // Current Price Section
                Text(
                    text = "Current Price",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                PriceRow("Last Price", ticker.lastPrice, isHighlighted = true)
                Spacer(modifier = Modifier.height(4.dp))
                
                val priceChangePercent = ticker.priceChangePercent.toDoubleOrNull() ?: 0.0
                val priceChangeColor = when {
                    priceChangePercent > 0 -> Color(0xFF4CAF50) // Green
                    priceChangePercent < 0 -> Color(0xFFF44336) // Red
                    else -> MaterialTheme.colorScheme.onSurface
                }
                
                PriceRow(
                    "24h Change", 
                    "${if (priceChangePercent >= 0) "+" else ""}${ticker.priceChangePercent}%",
                    valueColor = priceChangeColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                PriceRow("Price Change", ticker.priceChange)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 24h Statistics Section
                Text(
                    text = "24h Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                PriceRow("Open Price", ticker.openPrice)
                Spacer(modifier = Modifier.height(4.dp))
                PriceRow("Previous Close", ticker.prevClosePrice)
                Spacer(modifier = Modifier.height(4.dp))
                PriceRow("24h High", ticker.highPrice)
                Spacer(modifier = Modifier.height(4.dp))
                PriceRow("24h Low", ticker.lowPrice)
                Spacer(modifier = Modifier.height(4.dp))
                PriceRow("Weighted Avg", ticker.weightedAvgPrice)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Trading Info Section
                Text(
                    text = "Trading Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                PriceRow("Best Bid", "${ticker.bidPrice} (${ticker.bidQty})")
                Spacer(modifier = Modifier.height(4.dp))
                PriceRow("Best Ask", "${ticker.askPrice} (${ticker.askQty})")
                Spacer(modifier = Modifier.height(4.dp))
                PriceRow("Last Quantity", ticker.lastQty)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Volume Section
                Text(
                    text = "Volume",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                PriceRow("24h Volume (Quote)", ticker.quoteVolume.formatVolume())
                Spacer(modifier = Modifier.height(4.dp))
                PriceRow("24h Volume (Base)", ticker.volume.formatVolume())
                
            } else {
                Text(
                    text = "Price data not available",
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
fun NewsItemCard(newsItem: NewsItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable {
                // Clean URL before opening (remove any remaining CDATA or whitespace)
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
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = newsItem.pubDate.take(16), // Show date only
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