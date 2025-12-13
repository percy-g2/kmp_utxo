package ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import ktx.formatPrice
import model.OrderBookData
import theme.redDark
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Binance-style Order Book visualization
 * Side-by-side layout: Buy (left 50%), Sell (right 50%)
 * Depth bars rendered with sqrt normalization, max 80% width, opacity ≤ 0.25
 */
@Composable
fun OrderBookHeatMap(
    orderBookData: OrderBookData?,
    symbol: String,
    tradingPairs: List<model.TradingPair>,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Order Book",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (orderBookData == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading order book...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Get last traded price (mid price)
                val lastPrice = orderBookData.midPrice ?: orderBookData.bestBid?.priceDouble
                    ?: orderBookData.bestAsk?.priceDouble ?: 0.0
                
                // Get quote currency for display
                val quoteCurrency = tradingPairs.find { pair ->
                    symbol.endsWith(pair.quote, ignoreCase = true)
                }?.quote ?: "USDT"
                
                // Get base currency
                val baseCurrency = symbol.replace(quoteCurrency, "", ignoreCase = true)
                
                // Calculate height based on data
                val displayLevels = 10
                val rowHeight = 32.dp
                val dividerHeight = 8.dp // Divider with padding (4dp top + 4dp bottom)
                val maxRows = maxOf(
                    orderBookData.bids.take(displayLevels).size,
                    orderBookData.asks.take(displayLevels).size
                )
                // Calculate height: rows + divider (if rows >= 2, divider is inserted at middle)
                val calculatedHeight = remember(maxRows) {
                    val divider = if (maxRows >= 2) dividerHeight else 0.dp
                    (maxRows * rowHeight) + divider
                }
                
                // Column headers - side by side
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Buy column header (left 50%): Amount (left) | Price (right)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Amount ($baseCurrency)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Price ($quoteCurrency)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    
                    // Sell column header (right 50%): Price (left) | Amount (right)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Price ($quoteCurrency)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Amount ($baseCurrency)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Order book list - side by side columns with dynamic height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(calculatedHeight)
                ) {
                    OrderBookList(
                        orderBookData = orderBookData,
                        symbol = symbol,
                        tradingPairs = tradingPairs,
                        lastPrice = lastPrice,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderBookList(
    orderBookData: OrderBookData,
    symbol: String,
    tradingPairs: List<model.TradingPair>,
    lastPrice: Double,
    isDarkTheme: Boolean
) {
    // Subdued exchange-grade colors (not neon)
    val sellColor = Color(0xFFD32F2F) // Muted red
    val buyColor = if (isDarkTheme) Color(0xFF2E7D32) else Color(0xFF388E3C) // Muted green
    
    // Get top N levels for display
    val displayLevels = 10
    val asks = orderBookData.asks.take(displayLevels).reversed() // Highest ask first (descending)
    val bids = orderBookData.bids.take(displayLevels) // Highest bid first (descending)
    
    // Calculate cumulative quantity (not notional value) for each side
    val buyCumulativeQuantities = remember(bids) {
        var cumulative = 0.0
        bids.map { bid ->
            cumulative += bid.quantityDouble
            cumulative
        }
    }
    
    val sellCumulativeQuantities = remember(asks) {
        var cumulative = 0.0
        asks.map { ask ->
            cumulative += ask.quantityDouble
            cumulative
        }
    }
    
    // Find max cumulative quantity per side for normalization
    val maxBuyQty = remember(buyCumulativeQuantities) {
        buyCumulativeQuantities.maxOrNull() ?: 1.0
    }
    
    val maxSellQty = remember(sellCumulativeQuantities) {
        sellCumulativeQuantities.maxOrNull() ?: 1.0
    }
    
    // Fixed row height (32-36dp range, using 32dp for professional look)
    val rowHeight = 32.dp
    
    // Use single LazyColumn with rows containing both buy and sell for horizontal alignment
    val maxRows = maxOf(bids.size, asks.size)
    
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(maxRows) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
            ) {
                // Left 50%: Buy side
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (index < bids.size) {
                        val cumulativeQty = buyCumulativeQuantities[index]
                        val depthRatio by remember(cumulativeQty, maxBuyQty) {
                            derivedStateOf {
                                if (maxBuyQty > 0) {
                                    sqrt(cumulativeQty / maxBuyQty).coerceIn(0.0, 1.0)
                                } else {
                                    0.0
                                }
                            }
                        }
                        
                        OrderBookRowWithDepthBar(
                            price = bids[index].price,
                            quantity = bids[index].quantity,
                            symbol = symbol,
                            tradingPairs = tradingPairs,
                            isBuy = true,
                            lastPrice = lastPrice,
                            barColor = buyColor,
                            depthRatio = depthRatio,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                VerticalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    thickness = 1.dp
                )

                // Right 50%: Sell side
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (index < asks.size) {
                        val cumulativeQty = sellCumulativeQuantities[index]
                        val depthRatio by remember(cumulativeQty, maxSellQty) {
                            derivedStateOf {
                                if (maxSellQty > 0) {
                                    sqrt(cumulativeQty / maxSellQty).coerceIn(0.0, 1.0)
                                } else {
                                    0.0
                                }
                            }
                        }
                        
                        OrderBookRowWithDepthBar(
                            price = asks[index].price,
                            quantity = asks[index].quantity,
                            symbol = symbol,
                            tradingPairs = tradingPairs,
                            isBuy = false,
                            lastPrice = lastPrice,
                            barColor = sellColor,
                            depthRatio = depthRatio,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentPriceRow(
    price: Double,
    symbol: String,
    tradingPairs: List<model.TradingPair>,
    isDarkTheme: Boolean
) {
    val formattedPrice = remember(price, symbol, tradingPairs) {
        // Format price to 8 decimal places without String.format
        price.toString().formatPrice(symbol, tradingPairs)
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedPrice,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = redDark // Current price in red (like Binance)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formattedPrice, // Repeat price on right
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun OrderBookRowWithDepthBar(
    price: String,
    quantity: String,
    symbol: String,
    tradingPairs: List<model.TradingPair>,
    isBuy: Boolean,
    lastPrice: Double,
    barColor: Color,
    depthRatio: Double,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val priceDouble = price.toDoubleOrNull() ?: 0.0
    val isHighlighted = kotlin.math.abs(priceDouble - lastPrice) / lastPrice < 0.001 // Within 0.1%
    
    // Format price and quantity
    val formattedPrice = remember(price, symbol, tradingPairs) {
        formatOrderBookPrice(price, symbol, tradingPairs)
    }
    
    val formattedQuantity = remember(quantity) {
        formatOrderBookAmount(quantity)
    }
    
    // Calculate bar width: max 75% of side width (never overlap center), animated
    val targetBarWidth = remember(depthRatio) {
        (depthRatio.toFloat() * 0.75f).coerceIn(0f, 0.75f)
    }
    
    val animatedBarWidth by animateFloatAsState(
        targetValue = targetBarWidth,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "depthBarWidth"
    )
    
    // Bar opacity: 0.12-0.22 range (subdued, shadow-like)
    val barAlpha = remember(depthRatio) {
        if (depthRatio <= 0.0) {
            0f
        } else {
            // Map depthRatio [0, 1] to opacity [0.12, 0.22]
            (0.12f + (depthRatio.toFloat() * 0.10f)).coerceIn(0.12f, 0.22f)
        }
    }
    
    // Text colors: neutral, never blend with depth color
    // Always use high-contrast colors for readability
    val textColor = if (isDarkTheme) {
        Color(0xFFE0E0E0) // Light gray for dark theme
    } else {
        Color(0xFF212121) // Dark gray for light theme
    }
    val mutedTextColor = if (isDarkTheme) {
        Color(0xFFB0B0B0) // Muted light gray
    } else {
        Color(0xFF757575) // Muted dark gray
    }
    
    Box(
        modifier = modifier
    ) {
        // Depth bar background layer (bottom layer - shadow-like)
        if (isBuy) {
            // Buy: green bar, aligned CenterEnd, grows right → left
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedBarWidth)
                    .background(barColor.copy(alpha = barAlpha))
                    .align(Alignment.CenterEnd)
            )
        } else {
            // Sell: red bar, aligned CenterStart, grows left → right
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedBarWidth)
                    .background(barColor.copy(alpha = barAlpha))
                    .align(Alignment.CenterStart)
            )
        }
        
        // Text layer above depth bar (top layer - always readable)
        // Use SpaceBetween to ensure edge alignment: Buy side (Amount left, Price right), Sell side (Price left, Amount right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBuy) {
                // Buy side: Amount (left-aligned) | Price (right-aligned)
                Text(
                    text = formattedQuantity,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedTextColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formattedPrice,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHighlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        textColor
                    },
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Sell side: Price (left-aligned) | Amount (right-aligned)
                Text(
                    text = formattedPrice,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHighlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        textColor
                    },
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formattedQuantity,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedTextColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Format order book price with proper decimal precision
 * Uses the same approach as CryptoListScreen - use formatPrice for consistency
 * For non-USDT/USDC/FDUSD pairs, use the original price string as-is (no formatting)
 * Only convert exponential notation to decimal if present
 */
private fun formatOrderBookPrice(price: String, symbol: String, tradingPairs: List<model.TradingPair>): String {
    val selectedPair = tradingPairs.find { pair ->
        symbol.endsWith(pair.quote, ignoreCase = true)
    }?.quote.orEmpty()
    
    // For USDT/USDC/FDUSD: formatPrice handles formatting
    // For others (BTC, etc.): use original string as-is, but convert exponential to decimal if needed
    val normalizedPrice = if (selectedPair == "USDT" || selectedPair == "USDC" || selectedPair == "FDUSD") {
        // formatPrice will handle formatting for these
        price
    } else {
        // For non-USDT pairs, check if price has exponential notation
        if (price.contains('e', ignoreCase = true) || price.contains('E', ignoreCase = true)) {
            // Convert exponential to decimal format (no additional formatting)
            try {
                val priceValue = price.toDouble()
                // Format with enough precision to avoid exponential, then trim trailing zeros
                formatDoubleToString(priceValue, maxDecimals = 15)
            } catch (e: Exception) {
                price // Fallback to original if conversion fails
            }
        } else {
            // Already in decimal format, use as-is
            price
        }
    }
    
    // Use formatPrice to add currency symbol (same as CryptoListScreen)
    // For USDT/USDC/FDUSD: will format with formatAsCurrency
    // For others: will return normalizedPrice as-is with currency symbol
    return normalizedPrice.formatPrice(symbol, tradingPairs)
}

/**
 * Format order book amount (quantity) with proper decimal precision
 * For BTC and other crypto, show up to 8 decimal places
 * Kotlin Multiplatform-compatible (no String.format)
 */
private fun formatOrderBookAmount(quantity: String): String {
    return try {
        val quantityValue = quantity.toDouble()
        // Format with up to 8 decimal places, handling exponential notation if present
        formatDoubleToString(quantityValue, maxDecimals = 8)
    } catch (e: Exception) {
        // Fallback to original if parsing fails
        quantity
    }
}

/**
 * Format a Double to string with specified max decimal places
 * Kotlin Multiplatform-compatible (no String.format)
 */
private fun formatDoubleToString(value: Double, maxDecimals: Int): String {
    return try {
        // Handle very small numbers
        if (value == 0.0) return "0"
        
        // Build multiplier without using pow
        var multiplier = 1.0
        repeat(maxDecimals) {
            multiplier *= 10.0
        }
        
        val rounded = (value * multiplier).roundToInt()
        val result = rounded / multiplier
        
        // Convert to string and trim trailing zeros
        var resultStr = result.toString()
        
        // Handle scientific notation that might appear
        if (resultStr.contains('e', ignoreCase = true) || resultStr.contains('E', ignoreCase = true)) {
            // If still in exponential format, build decimal string manually
            val integerPart = result.toLong()
            val fractionalPart = ((result - integerPart) * multiplier).toLong()
            
            if (fractionalPart == 0L) {
                integerPart.toString()
            } else {
                val fractionalStr = fractionalPart.toString().padStart(maxDecimals, '0').trimEnd('0')
                if (fractionalStr.isEmpty()) {
                    integerPart.toString()
                } else {
                    "$integerPart.$fractionalStr"
                }
            }
        } else {
            // Trim trailing zeros and decimal point
            resultStr.trimEnd('0').trimEnd('.')
        }
    } catch (e: Exception) {
        // Fallback: use toString and clean up exponential notation manually
        value.toString().let { str ->
            if (str.contains('e', ignoreCase = true) || str.contains('E', ignoreCase = true)) {
                // Manual conversion for exponential - build multiplier without pow
                val parts = str.split('e', 'E', ignoreCase = true)
                if (parts.size == 2) {
                    val base = parts[0].toDoubleOrNull() ?: return str
                    val exponent = parts[1].toIntOrNull() ?: return str
                    
                    // Build multiplier for exponent
                    var expMultiplier = 1.0
                    val absExponent = kotlin.math.abs(exponent)
                    repeat(absExponent) {
                        expMultiplier *= 10.0
                    }
                    
                    val result = if (exponent >= 0) {
                        base * expMultiplier
                    } else {
                        base / expMultiplier
                    }
                    
                    // Format result with max decimals
                    formatDoubleToString(result, maxDecimals)
                } else {
                    str
                }
            } else {
                str.trimEnd('0').trimEnd('.')
            }
        }
    }
}
