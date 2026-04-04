package ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import ktx.formatAsCurrency
import model.OrderBookData
import model.OrderBookLevel
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Binance-style Order Book visualization
 * Side-by-side layout: Bid (left) | Ask (right)
 * Prices in CENTER, depth bars grow OUTWARD from center
 * Supports price grouping with dynamic options based on symbol price
 */
private const val ORDER_BOOK_DISPLAY_LEVELS = 10

/**
 * Data class for grouped order book level
 */
private data class GroupedLevel(
    val price: Double,
    val quantity: Double,
)

/**
 * Calculate dynamic price grouping options based on current price
 * Returns list of grouping values matching Binance's options for each price range
 * Handles boundary cases and ensures appropriate grouping for all price ranges
 */
private fun calculateGroupingOptions(currentPrice: Double): List<Double> {
    if (currentPrice <= 0.0) return listOf(0.01, 0.1, 1.0)

    val absPrice = kotlin.math.abs(currentPrice)

    return when {
        // Very high prices (>= 100000, e.g., BTC at $100k+): 1, 10, 50, 100, 500, 1000, 5000
        absPrice >= 100000.0 -> listOf(1.0, 10.0, 50.0, 100.0, 500.0, 1000.0, 5000.0)

        // Very high prices (>= 10000, e.g., BTC): 0.01, 0.1, 1, 10, 50, 100, 1000
        absPrice >= 10000.0 -> listOf(0.01, 0.1, 1.0, 10.0, 50.0, 100.0, 1000.0)

        // High prices (1000-9999, e.g., ETH): 0.01, 0.1, 1, 10, 50, 100
        absPrice >= 1000.0 -> listOf(0.01, 0.1, 1.0, 10.0, 50.0, 100.0)

        // Medium-high prices (100-999): 0.01, 0.1, 1, 10, 50
        absPrice >= 100.0 -> listOf(0.01, 0.1, 1.0, 10.0, 50.0)

        // Medium prices (10-99): 0.001, 0.01, 0.1, 1, 10
        absPrice >= 10.0 -> listOf(0.001, 0.01, 0.1, 1.0, 10.0)

        // Low prices (1-9): 0.0001, 0.001, 0.01, 0.1, 1
        absPrice >= 1.0 -> listOf(0.0001, 0.001, 0.01, 0.1, 1.0)

        // Very low prices (0.1-0.9): 0.00001, 0.0001, 0.001, 0.01, 0.1
        absPrice >= 0.1 -> listOf(0.00001, 0.0001, 0.001, 0.01, 0.1)

        // Very low prices (0.01-0.09): 0.000001, 0.00001, 0.0001, 0.001, 0.01
        absPrice >= 0.01 -> listOf(0.000001, 0.00001, 0.0001, 0.001, 0.01)

        // Extremely low prices (0.001-0.009): 0.0000001, 0.000001, 0.00001, 0.0001, 0.001
        absPrice >= 0.001 -> listOf(0.0000001, 0.000001, 0.00001, 0.0001, 0.001)

        // Ultra low prices (< 0.001): 0.00000001, 0.0000001, 0.000001, 0.00001, 0.0001
        else -> listOf(0.00000001, 0.0000001, 0.000001, 0.00001, 0.0001)
    }
}

/**
 * Format grouping value for display (KMP compatible)
 */
private fun formatGroupingValue(value: Double): String =
    when {
        value >= 1.0 -> value.toLong().toString()
        else -> {
            // Convert to string and clean up
            val str = value.toString()
            if (str.contains('E') || str.contains('e')) {
                // Handle scientific notation
                formatDoubleToString(value, maxDecimals = 8)
            } else {
                str.trimEnd('0').trimEnd('.')
            }
        }
    }

/**
 * Group order book levels by price bucket
 * For bids: round DOWN to nearest grouping (87859.99 with grouping 10 -> 87850)
 * For asks: round UP to nearest grouping (87860.01 with grouping 10 -> 87870)
 */
private fun groupOrderBookLevels(
    levels: List<OrderBookLevel>,
    grouping: Double,
    isBid: Boolean,
): List<GroupedLevel> {
    if (grouping <= 0.0 || levels.isEmpty()) {
        return levels.map {
            GroupedLevel(it.priceDouble, it.quantityDouble)
        }
    }

    val grouped = mutableMapOf<Double, Double>()

    levels.forEach { level ->
        val price = level.priceDouble
        val quantity = level.quantityDouble

        // Calculate bucket price
        val bucketPrice =
            if (isBid) {
                // For bids: round DOWN (floor)
                floor(price / grouping) * grouping
            } else {
                // For asks: round UP (ceil)
                kotlin.math.ceil(price / grouping) * grouping
            }

        // Aggregate quantity
        grouped[bucketPrice] = (grouped[bucketPrice] ?: 0.0) + quantity
    }

    // Sort and return
    return if (isBid) {
        // Bids: descending by price
        grouped.entries.sortedByDescending { it.key }.map { GroupedLevel(it.key, it.value) }
    } else {
        // Asks: ascending by price
        grouped.entries.sortedBy { it.key }.map { GroupedLevel(it.key, it.value) }
    }
}

@Composable
fun OrderBookHeatMap(
    orderBookData: OrderBookData?,
    orderBookError: String? = null,
    symbol: String,
    tradingPairs: List<model.TradingPair>,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    // Calculate dynamic grouping options based on current price
    val currentPrice =
        orderBookData?.midPrice
            ?: orderBookData?.bestBid?.priceDouble
            ?: orderBookData?.bestAsk?.priceDouble
            ?: 0.0

    val groupingOptions =
        remember(currentPrice) {
            calculateGroupingOptions(currentPrice)
        }

    // State for price grouping - default to smallest option
    var selectedGrouping by remember(groupingOptions) {
        mutableStateOf(groupingOptions.firstOrNull() ?: 0.01)
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(all = 8.dp),
        ) {
            Text(
                text = "Order Book",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
            )

            if (orderBookError != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Failed to load order book",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = orderBookError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else if (orderBookData == null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Loading order book...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Group the order book data based on selected grouping
                val groupedBids =
                    remember(orderBookData.bids, selectedGrouping) {
                        groupOrderBookLevels(orderBookData.bids, selectedGrouping, isBid = true)
                            .take(ORDER_BOOK_DISPLAY_LEVELS)
                    }

                val groupedAsks =
                    remember(orderBookData.asks, selectedGrouping) {
                        groupOrderBookLevels(orderBookData.asks, selectedGrouping, isBid = false)
                            .take(ORDER_BOOK_DISPLAY_LEVELS)
                    }

                // Calculate height based on data
                val rowHeight = 28.dp
                val maxRows = maxOf(groupedBids.size, groupedAsks.size)
                val calculatedHeight =
                    remember(maxRows) {
                        maxRows * rowHeight
                    }

                // Binance-style header: Bid | Ask | Dropdown
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Bid label (left, aligned to start)
                    Text(
                        text = "Bid",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )

                    // Ask label and dropdown (right side)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Ask label (aligned to start of right section)
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = "Ask",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )

                        // Price grouping dropdown (aligned to end)
                        Box {
                            Row(
                                modifier =
                                    Modifier
                                        .clickable { dropdownExpanded = true }
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = MaterialTheme.shapes.small,
                                        ).padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatGroupingValue(selectedGrouping),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select grouping",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                            ) {
                                groupingOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = formatGroupingValue(option),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        },
                                        onClick = {
                                            selectedGrouping = option
                                            dropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Order book list with grouped data
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(calculatedHeight),
                ) {
                    OrderBookList(
                        groupedBids = groupedBids,
                        groupedAsks = groupedAsks,
                        symbol = symbol,
                        tradingPairs = tradingPairs,
                        isDarkTheme = isDarkTheme,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderBookList(
    groupedBids: List<GroupedLevel>,
    groupedAsks: List<GroupedLevel>,
    symbol: String,
    tradingPairs: List<model.TradingPair>,
    isDarkTheme: Boolean,
) {
    // Get quantities for depth calculation
    val buyQuantities =
        remember(groupedBids) {
            groupedBids.map { it.quantity }
        }

    val sellQuantities =
        remember(groupedAsks) {
            groupedAsks.map { it.quantity }
        }

    // Find max quantity across BOTH sides for consistent normalization
    val maxQty =
        remember(buyQuantities, sellQuantities) {
            maxOf(
                buyQuantities.maxOrNull() ?: 1.0,
                sellQuantities.maxOrNull() ?: 1.0,
            )
        }

    val rowHeight = 28.dp
    val maxRows = maxOf(groupedBids.size, groupedAsks.size)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        items(maxRows) { index ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
            ) {
                // Left side: Bid
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) {
                    if (index < groupedBids.size) {
                        val level = groupedBids[index]
                        val depthRatio by remember(level.quantity, maxQty) {
                            derivedStateOf {
                                if (maxQty > 0) (level.quantity / maxQty).coerceIn(0.0, 1.0) else 0.0
                            }
                        }

                        OrderBookRow(
                            price = level.price,
                            quantity = level.quantity,
                            symbol = symbol,
                            tradingPairs = tradingPairs,
                            isBuy = true,
                            depthRatio = depthRatio,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Right side: Ask
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) {
                    if (index < groupedAsks.size) {
                        val level = groupedAsks[index]
                        val depthRatio by remember(level.quantity, maxQty) {
                            derivedStateOf {
                                if (maxQty > 0) (level.quantity / maxQty).coerceIn(0.0, 1.0) else 0.0
                            }
                        }

                        OrderBookRow(
                            price = level.price,
                            quantity = level.quantity,
                            symbol = symbol,
                            tradingPairs = tradingPairs,
                            isBuy = false,
                            depthRatio = depthRatio,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderBookRow(
    price: Double,
    quantity: Double,
    symbol: String,
    tradingPairs: List<model.TradingPair>,
    isBuy: Boolean,
    depthRatio: Double,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val formattedPrice =
        remember(price, symbol, tradingPairs) {
            formatOrderBookPrice(price, symbol, tradingPairs)
        }

    val formattedQuantity =
        remember(quantity) {
            formatOrderBookAmount(quantity)
        }

    val targetBarWidth =
        remember(depthRatio) {
            depthRatio.toFloat().coerceIn(0f, 1f)
        }

    val animatedBarWidth by animateFloatAsState(
        targetValue = targetBarWidth,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "depthBarWidth",
    )

    // Binance colors
    val buyColor = Color(0xFF0ECB81) // Binance green
    val sellColor = Color(0xFFF6465D) // Binance red
    val barAlpha = 0.15f

    // Quantity text color
    val quantityColor = if (isDarkTheme) Color(0xFFB0B0B0) else Color(0xFF666666)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Depth bar: grows OUTWARD from center
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedBarWidth)
                    .background(
                        if (isBuy) {
                            buyColor.copy(alpha = barAlpha)
                        } else {
                            sellColor.copy(alpha = barAlpha)
                        },
                    ).align(if (isBuy) Alignment.CenterEnd else Alignment.CenterStart),
        )

        // Text layer
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBuy) {
                // Bid: Quantity (far left) | Price (near center, GREEN)
                Text(
                    text = formattedQuantity,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = quantityColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formattedPrice,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = buyColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Ask: Price (near center, RED) | Quantity (far right)
                Text(
                    text = formattedPrice,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = sellColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formattedQuantity,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = quantityColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Calculate decimal precision based on price value
 * Matches Binance's precision display logic for all trading pairs
 */
private fun calculatePricePrecision(priceValue: Double): Int {
    if (priceValue <= 0.0) return 8

    val absValue = kotlin.math.abs(priceValue)

    return when {
        // Very high prices (>= 100000, e.g., BTC at $100k+): 0 decimal places
        absValue >= 100000.0 -> 0

        // High prices (10000-99999): 1 decimal place
        absValue >= 10000.0 -> 1

        // High prices (1000-9999, e.g., ETH): 2 decimal places
        absValue >= 1000.0 -> 2

        // Medium-high prices (100-999): 3 decimal places
        absValue >= 100.0 -> 3

        // Medium prices (10-99): 4 decimal places
        absValue >= 10.0 -> 4

        // Low prices (1-9): 5 decimal places
        absValue >= 1.0 -> 5

        // Very low prices (0.1-0.9): 6 decimal places
        absValue >= 0.1 -> 6

        // Very low prices (0.01-0.09): 7 decimal places
        absValue >= 0.01 -> 7

        // Extremely low prices (0.001-0.009): 8 decimal places
        absValue >= 0.001 -> 8

        // Ultra low prices (< 0.001): 8 decimal places (max precision)
        else -> 8
    }
}

/**
 * Format order book price (Double version)
 * Handles all trading pair types consistently like Binance app
 */
private fun formatOrderBookPrice(
    price: Double,
    symbol: String,
    tradingPairs: List<model.TradingPair>,
): String {
    val selectedPair =
        tradingPairs
            .find { pair ->
                symbol.endsWith(pair.quote, ignoreCase = true)
            }?.quote
            .orEmpty()

    // For USD-stablecoin pairs (USDT, USDC, FDUSD, USD1), use currency formatting
    if (selectedPair == "USDT" || selectedPair == "USDC" || selectedPair == "FDUSD" || selectedPair == "USD1") {
        return price.formatAsCurrency()
    }

    // For BTC pairs (e.g., ETHBTC, ALTBTC), use higher precision
    if (selectedPair == "BTC") {
        val precision = calculatePricePrecision(price)
        // Ensure minimum 8 decimals for BTC pairs (Binance standard)
        return formatDoubleToString(price, maxDecimals = maxOf(precision, 8))
    }

    // For ETH pairs (e.g., ALTHETH), use higher precision
    if (selectedPair == "ETH") {
        val precision = calculatePricePrecision(price)
        // Ensure minimum 6 decimals for ETH pairs
        return formatDoubleToString(price, maxDecimals = maxOf(precision, 6))
    }

    // For other pairs (BNB, BUSD, etc.), use calculated precision
    val precision = calculatePricePrecision(price)
    return formatDoubleToString(price, maxDecimals = precision)
}

/**
 * Format order book quantity (Double version)
 * Uses dynamic precision and formats large quantities with abbreviations (K, M, B) like Binance
 */
private fun formatOrderBookAmount(quantity: Double): String {
    if (quantity <= 0.0) return "0"

    val absQuantity = kotlin.math.abs(quantity)

    // Format large quantities with abbreviations (Binance style)
    return when {
        // Billions: format as "X.XXB"
        absQuantity >= 1_000_000_000.0 -> {
            val billions = absQuantity / 1_000_000_000.0
            val formatted = formatDoubleToString(billions, maxDecimals = 2)
            "${formatted}B"
        }

        // Millions: format as "X.XXM"
        absQuantity >= 1_000_000.0 -> {
            val millions = absQuantity / 1_000_000.0
            val formatted = formatDoubleToString(millions, maxDecimals = 2)
            "${formatted}M"
        }

        // Thousands: format as "X.XXK"
        absQuantity >= 1_000.0 -> {
            val thousands = absQuantity / 1_000.0
            val formatted = formatDoubleToString(thousands, maxDecimals = 2)
            "${formatted}K"
        }

        // Small quantities: use dynamic precision based on value
        else -> {
            val precision =
                when {
                    absQuantity >= 1.0 -> 5
                    absQuantity >= 0.1 -> 6
                    absQuantity >= 0.01 -> 7
                    absQuantity >= 0.001 -> 8
                    else -> 8
                }
            formatDoubleToString(quantity, maxDecimals = precision)
        }
    }
}

/**
 * Format double to string with max decimals
 */
private fun formatDoubleToString(
    value: Double,
    maxDecimals: Int,
): String {
    return try {
        if (value == 0.0) return "0"

        var multiplier = 1.0
        repeat(maxDecimals) {
            multiplier *= 10.0
        }

        val rounded = (value * multiplier).roundToLong()
        val result = rounded.toDouble() / multiplier

        val resultStr = result.toString()

        if (resultStr.contains('e', ignoreCase = true) || resultStr.contains('E', ignoreCase = true)) {
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
            resultStr.trimEnd('0').trimEnd('.')
        }
    } catch (_: Exception) {
        value.toString().let { str ->
            if (str.contains('e', ignoreCase = true) || str.contains('E', ignoreCase = true)) {
                val parts = str.split('e', 'E', ignoreCase = true)
                if (parts.size == 2) {
                    val base = parts[0].toDoubleOrNull() ?: return str
                    val exponent = parts[1].toIntOrNull() ?: return str

                    var expMultiplier = 1.0
                    val absExponent = kotlin.math.abs(exponent)
                    repeat(absExponent) {
                        expMultiplier *= 10.0
                    }

                    val calcResult =
                        if (exponent >= 0) {
                            base * expMultiplier
                        } else {
                            base / expMultiplier
                        }

                    formatDoubleToString(calcResult, maxDecimals)
                } else {
                    str
                }
            } else {
                str.trimEnd('0').trimEnd('.')
            }
        }
    }
}
