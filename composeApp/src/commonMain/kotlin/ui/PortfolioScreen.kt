package ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import ktx.formatAsCurrency
import model.PerpPositionRow
import model.PortfolioSummary
import model.PortfolioUiState
import model.SpotBalanceRow
import org.jetbrains.compose.resources.stringResource
import theme.ThemeManager.store
import ui.utils.getPriceChangeColor
import ui.utils.isDarkTheme
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.portfolio_account
import utxo.composeapp.generated.resources.portfolio_allocation
import utxo.composeapp.generated.resources.portfolio_available
import utxo.composeapp.generated.resources.portfolio_away
import utxo.composeapp.generated.resources.portfolio_empty
import utxo.composeapp.generated.resources.portfolio_empty_hint
import utxo.composeapp.generated.resources.portfolio_entry
import utxo.composeapp.generated.resources.portfolio_free
import utxo.composeapp.generated.resources.portfolio_hold
import utxo.composeapp.generated.resources.portfolio_invalid_address
import utxo.composeapp.generated.resources.portfolio_invalid_address_hint
import utxo.composeapp.generated.resources.portfolio_liq
import utxo.composeapp.generated.resources.portfolio_live
import utxo.composeapp.generated.resources.portfolio_load_error
import utxo.composeapp.generated.resources.portfolio_load_error_hint
import utxo.composeapp.generated.resources.portfolio_long
import utxo.composeapp.generated.resources.portfolio_margin_used
import utxo.composeapp.generated.resources.portfolio_mark
import utxo.composeapp.generated.resources.portfolio_no_address
import utxo.composeapp.generated.resources.portfolio_no_address_hint
import utxo.composeapp.generated.resources.portfolio_open_settings
import utxo.composeapp.generated.resources.portfolio_positions
import utxo.composeapp.generated.resources.portfolio_reconnecting
import utxo.composeapp.generated.resources.portfolio_retry
import utxo.composeapp.generated.resources.portfolio_short
import utxo.composeapp.generated.resources.portfolio_size
import utxo.composeapp.generated.resources.portfolio_spot
import utxo.composeapp.generated.resources.portfolio_title
import utxo.composeapp.generated.resources.portfolio_value
import utxo.composeapp.generated.resources.refresh
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Validates a Hyperliquid (EVM) wallet address: "0x" followed by 40 hex characters.
 * Shared by the Settings input, the bottom-bar tab gate, and [PortfolioViewModel].
 */
fun isValidHyperliquidAddress(address: String): Boolean =
    address.length == 42 &&
        address.startsWith("0x") &&
        address.drop(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onConfigureClick: () -> Unit,
    viewModel: PortfolioViewModel = viewModel { PortfolioViewModel() },
) {
    val state by viewModel.state.collectAsState()
    val settings by store.updates.collectAsState(initial = null)
    val isDark = isDarkTheme(settings)
    val lifecycleOwner = LocalLifecycleOwner.current

    // Pause the live socket when the screen is not visible (battery/data), resume on return.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resume()
                Lifecycle.Event.ON_PAUSE -> viewModel.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pause()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.portfolio_title)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.refresh))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val s = state) {
                PortfolioUiState.Loading -> LoadingSkeleton()

                PortfolioUiState.NoAddress -> InfoState(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = stringResource(Res.string.portfolio_no_address),
                    hint = stringResource(Res.string.portfolio_no_address_hint),
                    actionText = stringResource(Res.string.portfolio_open_settings),
                    onAction = onConfigureClick,
                )

                PortfolioUiState.InvalidAddress -> InfoState(
                    icon = Icons.Default.ErrorOutline,
                    title = stringResource(Res.string.portfolio_invalid_address),
                    hint = stringResource(Res.string.portfolio_invalid_address_hint),
                    actionText = stringResource(Res.string.portfolio_open_settings),
                    onAction = onConfigureClick,
                )

                PortfolioUiState.Error -> InfoState(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(Res.string.portfolio_load_error),
                    hint = stringResource(Res.string.portfolio_load_error_hint),
                    actionText = stringResource(Res.string.portfolio_retry),
                    onAction = viewModel::refresh,
                )

                is PortfolioUiState.Data -> PortfolioContent(s, isDark)
            }
        }
    }
}

@Composable
private fun PortfolioContent(data: PortfolioUiState.Data, isDark: Boolean) {
    val palette = coinPalette()
    val showPerpStats = data.perpPositions.isNotEmpty() || data.summary.accountValue > 0.0

    // Allocation segments across USD-valued holdings (perp notionals + stable spot).
    val allocation = buildList {
        data.perpPositions.forEach { add(Triple(it.coin, it.notionalUsd, palette.colorFor(it.coin))) }
        data.spotBalances.forEach { b -> b.usdValue?.let { add(Triple(b.coin, it, palette.colorFor(b.coin))) } }
    }.filter { it.second > 0.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") { HeroCard(data.summary, data.isStale, showPerpStats, isDark) }

        if (allocation.size >= 2) {
            item(key = "allocation") { AllocationStrip(allocation) }
        }

        if (data.perpPositions.isNotEmpty()) {
            item(key = "perp_header") {
                SectionHeader(stringResource(Res.string.portfolio_positions), data.perpPositions.size)
            }
            items(items = data.perpPositions, key = { "perp_${it.coin}" }) { row ->
                PositionCard(row, palette.colorFor(row.coin), isDark)
            }
        }

        if (data.spotBalances.isNotEmpty()) {
            item(key = "spot_header") {
                SectionHeader(stringResource(Res.string.portfolio_spot), data.spotBalances.size)
            }
            items(items = data.spotBalances, key = { "spot_${it.coin}" }) { row ->
                SpotCard(row, palette.colorFor(row.coin))
            }
        }

        if (data.perpPositions.isEmpty() && data.spotBalances.isEmpty()) {
            item(key = "empty") { EmptyHoldings() }
        }
    }
}

// region --- Hero ---

@Composable
private fun HeroCard(summary: PortfolioSummary, isStale: Boolean, showPerpStats: Boolean, isDark: Boolean) {
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    val sheen = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
            Color.Transparent,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f),
        )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .background(sheen)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.portfolio_value),
                    style = MaterialTheme.typography.labelLarge,
                    color = onHero.copy(alpha = 0.75f),
                )
                LiveChip(isStale, onHero)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = summary.totalValue.toUsd(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = onHero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showPerpStats) {
                Spacer(Modifier.height(10.dp))
                PnlPill(summary.totalUnrealizedPnl, summary.pnlPercent, isDark)
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    HeroStat(stringResource(Res.string.portfolio_account), summary.accountValue.toUsd(), onHero, Modifier.weight(1f))
                    HeroStat(stringResource(Res.string.portfolio_margin_used), summary.totalMarginUsed.toUsd(), onHero, Modifier.weight(1f))
                    HeroStat(stringResource(Res.string.portfolio_free), summary.withdrawable.toUsd(), onHero, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LiveChip(isStale: Boolean, onHero: Color) {
    val dot = if (isStale) MaterialTheme.colorScheme.error else getPriceChangeColor(1f, isDarkTheme = false, primaryColor = onHero)
    val label = if (isStale) stringResource(Res.string.portfolio_reconnecting) else stringResource(Res.string.portfolio_live)
    Surface(
        shape = RoundedCornerShape(50),
        color = onHero.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Text(label, style = MaterialTheme.typography.labelSmall, color = onHero)
        }
    }
}

@Composable
private fun PnlPill(pnl: Double, pnlPercent: Double?, isDark: Boolean) {
    val color = getPriceChangeColor(pnl.toFloat(), isDark, MaterialTheme.colorScheme.onPrimaryContainer)
    val pct = pnlPercent?.let { " (${it.toSignedPercent()})" }.orEmpty()
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (pnl < 0) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = pnl.toSignedUsd() + pct,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, onHero: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = onHero.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleSmall, color = onHero, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// endregion

// region --- Allocation ---

@Composable
private fun AllocationStrip(items: List<Triple<String, Double, Color>>) {
    val total = items.sumOf { it.second }
    if (total <= 0.0) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = stringResource(Res.string.portfolio_allocation),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { (_, value, color) ->
                Box(
                    Modifier
                        .weight((value / total).toFloat().coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(color),
                )
            }
        }
    }
}

// endregion

// region --- Sections & rows ---

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PositionCard(row: PerpPositionRow, accent: Color, isDark: Boolean) {
    val pnlColor = getPriceChangeColor(row.unrealizedPnl.toFloat(), isDark, MaterialTheme.colorScheme.onSurface)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinMonogram(row.coin, accent)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.coin, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SidePill(row.isLong, isDark)
                        if (row.leverageText.isNotBlank()) Tag(row.leverageText)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${stringResource(Res.string.portfolio_size)} ${row.size.toAmount()}  ·  " +
                            "${stringResource(Res.string.portfolio_entry)} ${row.entryPx.toPriceOrDash()}  ·  " +
                            "${stringResource(Res.string.portfolio_mark)} ${row.markPx.toPriceOrDash()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(row.unrealizedPnl.toSignedUsd(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = pnlColor)
                    Text(row.roePercent.toSignedPercent(), style = MaterialTheme.typography.labelMedium, color = pnlColor)
                }
            }
            if (row.liquidationPx != null && row.liqDistanceFraction != null && row.markPx != null) {
                Spacer(Modifier.height(10.dp))
                LiquidationBar(row.liqDistanceFraction, row.liquidationPx, row.markPx, isDark)
            }
        }
    }
}

@Composable
private fun LiquidationBar(fraction: Double, liqPx: Double, markPx: Double, isDark: Boolean) {
    val safe = fraction.toFloat().coerceIn(0f, 1f)
    // Healthy (far from liq) -> green, close -> red.
    val barColor = getPriceChangeColor(if (safe > 0.35f) 1f else -1f, isDark, MaterialTheme.colorScheme.primary)
    val awayPct = if (markPx != 0.0) abs(markPx - liqPx) / markPx * 100.0 else 0.0
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(safe)
                    .fillMaxHeight()
                    .background(barColor),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${stringResource(Res.string.portfolio_liq)} ${liqPx.let { it.toPriceValue() }}  ·  ${awayPct.toAmount()}% ${stringResource(Res.string.portfolio_away)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpotCard(row: SpotBalanceRow, accent: Color) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoinMonogram(row.coin, accent)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.coin, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (row.hold > 0.0) {
                    Text(
                        text = "${stringResource(Res.string.portfolio_available)} ${row.available.toAmount()}  ·  " +
                            "${stringResource(Res.string.portfolio_hold)} ${row.hold.toAmount()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(row.total.toAmount(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                row.usdValue?.let {
                    Text(it.toUsd(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CoinMonogram(coin: String, accent: Color) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = coin.take(if (coin.length >= 2) 2 else 1).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

@Composable
private fun SidePill(isLong: Boolean, isDark: Boolean) {
    val color = getPriceChangeColor(if (isLong) 1f else -1f, isDark, MaterialTheme.colorScheme.primary)
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f)) {
        Text(
            text = if (isLong) stringResource(Res.string.portfolio_long) else stringResource(Res.string.portfolio_short),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Tag(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// endregion

// region --- States ---

@Composable
private fun EmptyHoldings() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.portfolio_empty), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.portfolio_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InfoState(
    icon: ImageVector,
    title: String,
    hint: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onAction) { Text(actionText) }
    }
}

@Composable
private fun LoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(850), repeatMode = RepeatMode.Reverse),
        label = "alpha",
    )
    val shimmer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp)).background(shimmer))
        repeat(4) {
            Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(18.dp)).background(shimmer))
        }
    }
}

// endregion

// region --- colors & formatting ---

private class CoinPalette(private val colors: List<Color>) {
    fun colorFor(coin: String): Color = colors[(abs(coin.hashCode()) % colors.size)]
}

@Composable
private fun coinPalette(): CoinPalette = CoinPalette(
    listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        getPriceChangeColor(1f, isDarkTheme = true, primaryColor = MaterialTheme.colorScheme.primary),
        MaterialTheme.colorScheme.error,
    )
)

private fun Double.toUsd(): String {
    val formatted = abs(this).formatAsCurrency()
    return if (this < 0) "-$$formatted" else "$$formatted"
}

private fun Double.toSignedUsd(): String {
    val sign = if (this < 0) "-" else "+"
    return "$sign$" + abs(this).formatAsCurrency()
}

private fun Double.toSignedPercent(): String {
    val sign = if (this < 0) "-" else "+"
    return "$sign${abs(this).toAmount()}%"
}

private fun Double.toPriceValue(): String =
    if (abs(this) >= 1.0) "$" + this.formatAsCurrency() else "$" + this.toAmount()

private fun Double?.toPriceOrDash(): String {
    val v = this ?: return "—"
    if (v == 0.0) return "—"
    return v.toPriceValue()
}

private fun Double.toAmount(): String {
    if (this == 0.0) return "0"
    val rounded = (this * 1_000_000).roundToLong() / 1_000_000.0
    val asLong = rounded.toLong()
    return if (rounded == asLong.toDouble()) asLong.toString()
    else rounded.toString().trimEnd('0').trimEnd('.')
}

// endregion
