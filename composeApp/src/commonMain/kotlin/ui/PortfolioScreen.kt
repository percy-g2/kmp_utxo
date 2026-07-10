package ui

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import ktx.formatAsCurrency
import model.PerpPositionRow
import model.PortfolioSummary
import model.PortfolioUiState
import model.SCOPE_ALL
import model.SpotBalanceRow
import model.WalletTab
import org.jetbrains.compose.resources.stringResource
import theme.ThemeManager.store
import ui.components.CoinIcon
import ui.components.LazyColumnScrollbar
import ui.components.ScrollToEdgeButton
import ui.utils.bottomBarClearancePadding
import ui.utils.getPriceChangeColor
import ui.utils.isDarkTheme
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.portfolio_account
import utxo.composeapp.generated.resources.portfolio_all
import utxo.composeapp.generated.resources.portfolio_alloc_bar
import utxo.composeapp.generated.resources.portfolio_alloc_donut
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
import utxo.composeapp.generated.resources.portfolio_total
import utxo.composeapp.generated.resources.portfolio_value
import utxo.composeapp.generated.resources.refresh
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToLong
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onConfigureClick: () -> Unit,
    viewModel: PortfolioViewModel = viewModel { PortfolioViewModel() },
    // Bottom clearance for the iOS 26 native Liquid Glass tab bar. On that path the screen applies
    // only the top window inset to its body so the list flows UNDER the bar (frosting through it),
    // then reserves this footprint as the list's bottom contentPadding so the last card still scrolls
    // clear. Defaults to 0.dp on every other path (App() reserves its own bottom bar) → no change.
    bottomBarClearance: Dp = 0.dp,
) {
    val state by viewModel.state.collectAsState()
    val walletTabs by viewModel.walletTabs.collectAsState()
    val selectedScope by viewModel.selectedScope.collectAsState()
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
                },
                // The root Scaffold already applies the status-bar inset to this screen,
                // so the nested top bar must not add it again (matches SettingsScreen).
                windowInsets = WindowInsets(top = 0.dp, bottom = 0.dp),
            )
        }
    ) { innerPadding ->
        // The list flows under the iOS 26 glass tab bar; reserve the bar's footprint as bottom
        // contentPadding so the last card still scrolls clear of it. No-op off the iOS 26 path
        // (bottomBarClearance is 0). See ui.utils.bottomBarClearancePadding.
        val extraBottom = bottomBarClearancePadding(bottomBarClearance)
        // On the iOS 26 path apply only the top inset so the body reaches the screen bottom and the
        // list flows UNDER the glass bar (extraBottom keeps the last card scrollable clear); off that
        // path reserve the whole innerPadding exactly as before.
        val bodyModifier = if (bottomBarClearance > 0.dp) {
            Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())
        } else {
            Modifier.fillMaxSize().padding(innerPadding)
        }
        Column(modifier = bodyModifier) {
            // The switcher is pinned above the body and stays visible in every state, so the
            // user can switch away from a wallet that is loading or errored. Only shown with
            // 2+ wallets (tabs = "All" + one per wallet), so a single wallet keeps the
            // original switcher-free layout.
            if (walletTabs.size > 2) {
                WalletScopeSwitcher(
                    tabs = walletTabs,
                    selectedKey = selectedScope,
                    onSelect = viewModel::selectScope,
                    isDark = isDark,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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

                    is PortfolioUiState.Data -> PortfolioContent(s, isDark, extraBottom)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletScopeSwitcher(
    tabs: List<WalletTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    isDark: Boolean,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = tabs, key = { it.key }) { tab ->
            val label = if (tab.key == SCOPE_ALL) stringResource(Res.string.portfolio_all) else tab.label
            val dot = if (tab.isStale) {
                MaterialTheme.colorScheme.error
            } else {
                getPriceChangeColor(1f, isDark, MaterialTheme.colorScheme.primary)
            }
            FilterChip(
                selected = tab.key == selectedKey,
                onClick = { onSelect(tab.key) },
                label = {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                },
            )
        }
    }
}

@Composable
private fun PortfolioContent(data: PortfolioUiState.Data, isDark: Boolean, extraBottomPadding: Dp) {
    val palette = coinPalette()
    val listState = rememberLazyListState()
    val showPerpStats = data.perpPositions.isNotEmpty() || data.summary.accountValue > 0.0

    // Allocation slices across USD-valued holdings (perp notionals + stable spot), largest first.
    // Colors are assigned by index (not by coin hash) so the biggest slices always get the most
    // distinct palette entries and the legend never shows two coins in one color (for <= 5 slices).
    // Each slice keeps a stable id (instrument+coin+wallet) so the same coin held across several
    // wallets stays independently selectable in the aggregate "All" view. Color/fraction are filled
    // after sorting, so the seeds are built with placeholders.
    // remember keyed on the only inputs that affect the breakdown, so it is NOT rebuilt+sorted
    // inside composition on every unrelated recomposition (scroll frames) or no-op price tick.
    val slices = remember(data.perpPositions, data.spotBalances, palette) {
        val seeds = buildList {
            data.perpPositions.forEach {
                if (it.notionalUsd > 0.0) {
                    add(AllocSlice("perp|${it.coin}|${it.sourceLabel.orEmpty()}", it.coin, it.sourceLabel, it.notionalUsd, Color.Transparent, 0f))
                }
            }
            data.spotBalances.forEach { b ->
                b.usdValue?.let {
                    if (it > 0.0) {
                        add(AllocSlice("spot|${b.coin}|${b.sourceLabel.orEmpty()}", b.coin, b.sourceLabel, it, Color.Transparent, 0f))
                    }
                }
            }
        }.sortedByDescending { it.usd }
        val total = seeds.sumOf { it.usd }
        seeds.mapIndexed { i, s ->
            s.copy(color = palette.colorAt(i), fraction = if (total > 0.0) (s.usd / total).toFloat() else 0f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + extraBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") { HeroCard(data.summary, data.isStale, showPerpStats, isDark) }

            if (slices.size >= 2) {
                item(key = "allocation") { AllocationSection(slices) }
            }

            if (data.perpPositions.isNotEmpty()) {
                item(key = "perp_header") {
                    SectionHeader(stringResource(Res.string.portfolio_positions), data.perpPositions.size)
                }
                items(items = data.perpPositions, key = { "perp_${it.sourceLabel}_${it.coin}" }) { row ->
                    PositionCard(row, palette.colorFor(row.coin), isDark)
                }
            }

            if (data.spotBalances.isNotEmpty()) {
                item(key = "spot_header") {
                    SectionHeader(stringResource(Res.string.portfolio_spot), data.spotBalances.size)
                }
                items(items = data.spotBalances, key = { "spot_${it.sourceLabel}_${it.coin}" }) { row ->
                    SpotCard(row, palette.colorFor(row.coin))
                }
            }

            if (data.perpPositions.isEmpty() && data.spotBalances.isEmpty()) {
                item(key = "empty") { EmptyHoldings() }
            }
        }

        LazyColumnScrollbar(listState = listState)
        // Gate the totalItemsCount read behind derivedStateOf so scrolling (which changes
        // layoutInfo every frame) only re-triggers this button when the item COUNT actually changes.
        val scrollTotal by remember(listState) {
            derivedStateOf { listState.layoutInfo.totalItemsCount }
        }
        ScrollToEdgeButton(
            listState = listState,
            totalItems = scrollTotal,
            bottomBarClearance = extraBottomPadding,
        )
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

/**
 * One allocation slice. [id] is a stable per-holding identity (instrument+coin+wallet) so that, in the
 * aggregate "All" view, the same coin held in several wallets yields independently selectable slices.
 * [sourceLabel] is the owning wallet's name (non-null only in the "All" view).
 */
private data class AllocSlice(
    val id: String,
    val coin: String,
    val sourceLabel: String?,
    val usd: Double,
    val color: Color,
    val fraction: Float,
)

private enum class AllocView { Bar, Donut }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllocationSection(slices: List<AllocSlice>) {
    // Selected slice id — tap a slice/legend chip to select, tap again to clear. Self-contained: it
    // only drives the highlight + caption, with no coupling to the list's scroll position. [view] is
    // the bar/donut toggle; both views share this selection.
    var selected by remember { mutableStateOf<String?>(null) }
    var view by remember { mutableStateOf(AllocView.Bar) }
    // The list updates live; ignore a stale selection whose slice is no longer present (e.g. a closed
    // position) so nothing dims with no slice highlighted.
    val active = selected?.takeIf { sel -> slices.any { it.id == sel } }
    val onSelect: (String) -> Unit = { id -> selected = if (selected == id) null else id }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.portfolio_allocation),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AllocViewToggle(view, onChange = { view = it })
        }
        Spacer(Modifier.height(10.dp))

        when (view) {
            AllocView.Bar -> AllocationBar(slices, active, onSelect)
            AllocView.Donut -> AllocationDonut(slices, active, onSelect)
        }

        // Caption: shown only while a slice is selected — an addition on tap, never a hidden default.
        active?.let { id ->
            slices.firstOrNull { it.id == id }?.let { slice ->
                Spacer(Modifier.height(8.dp))
                val walletPart = slice.sourceLabel?.let { "  ·  $it" }.orEmpty()
                Text(
                    text = "${slice.coin.substringAfterLast(':')}$walletPart  ·  ${(slice.fraction * 100.0).toPercent(1)}%  ·  ${slice.usd.toUsd()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Legend: always visible (color dot · coin · %). The chip is the reliable accessible tap target.
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            slices.forEach { slice ->
                LegendChip(slice, active == slice.id) { onSelect(slice.id) }
            }
        }
    }
}

/** Compact segmented control switching the allocation between the bar and the donut view. */
@Composable
private fun AllocViewToggle(view: AllocView, onChange: (AllocView) -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Row(modifier = Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            AllocViewToggleButton(
                icon = Icons.Default.BarChart,
                description = stringResource(Res.string.portfolio_alloc_bar),
                selected = view == AllocView.Bar,
            ) { onChange(AllocView.Bar) }
            AllocViewToggleButton(
                icon = Icons.Default.DonutLarge,
                description = stringResource(Res.string.portfolio_alloc_donut),
                selected = view == AllocView.Donut,
            ) { onChange(AllocView.Donut) }
        }
    }
}

@Composable
private fun AllocViewToggleButton(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AllocationBar(slices: List<AllocSlice>, activeId: String?, onSelect: (String) -> Unit) {
    // The 28dp-tall Row gives each slice a real touch target; the visible fill is 12dp (16dp when
    // selected). Non-selected slices dim while a selection is active.
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slices.forEach { slice ->
            val isSel = activeId == slice.id
            val dimmed = activeId != null && !isSel
            Box(
                modifier = Modifier
                    .weight(slice.fraction.coerceAtLeast(0.03f))
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(slice.id) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (isSel) 16.dp else 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(slice.color.copy(alpha = if (dimmed) 0.35f else 1f)),
                )
            }
        }
    }
}

/**
 * Donut rendering of the same slices, tappable by arc. Taps are hit-tested by angle+radius against the
 * slice ranges (arcs run clockwise from 12 o'clock), so a tap on the ring selects its slice. The center
 * shows the total, or the selected slice's coin + share when one is chosen.
 */
@Composable
private fun AllocationDonut(slices: List<AllocSlice>, activeId: String?, onSelect: (String) -> Unit) {
    val total = slices.sumOf { it.usd }
    val selected = activeId?.let { id -> slices.firstOrNull { it.id == id } }
    val gapDeg = 2f

    Box(modifier = Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(184.dp)
                .pointerInput(slices) {
                    detectTapGestures { tap ->
                        val dx = tap.x - size.width / 2f
                        val dy = tap.y - size.height / 2f
                        val dist = sqrt(dx * dx + dy * dy)
                        val outer = minOf(size.width, size.height) / 2f
                        // React only to taps roughly on the ring; ignore the center label and far corners.
                        if (dist < outer * 0.5f || dist > outer) return@detectTapGestures
                        var ang = atan2(dy, dx) * (180f / PI.toFloat()) // 0 = 3 o'clock, clockwise
                        if (ang < -90f) ang += 360f                     // arcs run [-90, 270)
                        var start = -90f
                        for (s in slices) {
                            val sweep = s.fraction * 360f
                            if (ang >= start && ang < start + sweep) {
                                onSelect(s.id)
                                break
                            }
                            start += sweep
                        }
                    }
                },
        ) {
            val stroke = 26.dp.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            var start = -90f
            slices.forEach { slice ->
                val sweep = slice.fraction * 360f
                val dim = activeId != null && activeId != slice.id
                drawArc(
                    color = slice.color.copy(alpha = if (dim) 0.3f else 1f),
                    startAngle = start + gapDeg / 2f,
                    sweepAngle = (sweep - gapDeg).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selected != null) {
                Text(
                    text = selected.coin.substringAfterLast(':'),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = "${(selected.fraction * 100.0).toPercent(1)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(Res.string.portfolio_total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = total.toUsd(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LegendChip(slice: AllocSlice, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            slice.color.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(slice.color))
            Text(
                text = slice.coin.substringAfterLast(':'),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // Wallet name disambiguates same-coin slices in the aggregate "All" view (null otherwise).
            slice.sourceLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${(slice.fraction * 100.0).toPercent(1)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                CoinIcon(
                    baseAsset = row.coin.substringAfterLast(':'),
                    modifier = Modifier.size(40.dp),
                    fallback = { CoinMonogramFallback(row.coin, accent) },
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.coin, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SidePill(row.isLong, isDark)
                        if (row.leverageText.isNotBlank()) Tag(row.leverageText)
                    }
                    row.sourceLabel?.let { WalletTagLine(it) }
                    // Prominent live mark price (flashes green/red on each Hyperliquid tick), then the
                    // secondary Size · Entry detail — mark used to be buried in that gray line.
                    Spacer(Modifier.height(4.dp))
                    LiveMarkPrice(row.markPx, isDark)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${stringResource(Res.string.portfolio_size)} ${row.size.toAmount()}  ·  " +
                            "${stringResource(Res.string.portfolio_entry)} ${row.entryPx.toPriceOrDash()}",
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

/**
 * The position's live mark price, prominent. Flashes green (up) / red (down) for one beat on each
 * change, then decays back to the resting color. Source is the existing [PerpPositionRow.markPx],
 * which Hyperliquid refreshes on every webData2 frame — this is presentation only, no new data.
 */
@Composable
private fun LiveMarkPrice(markPx: Double?, isDark: Boolean, modifier: Modifier = Modifier) {
    val resting = MaterialTheme.colorScheme.onSurface
    val color = remember { Animatable(resting) }
    var caret by remember { mutableIntStateOf(0) }         // -1 down, 0 none, +1 up
    var prev by remember { mutableStateOf<Double?>(null) } // null => first composition, don't flash

    LaunchedEffect(markPx) {
        val cur = markPx
        val old = prev
        prev = cur
        // No flash on the first value (old == null), on a missing price, or on an unchanged tick.
        if (cur == null || old == null || cur == old) return@LaunchedEffect
        val delta = (cur - old).toFloat()
        caret = if (delta > 0f) 1 else -1
        color.snapTo(getPriceChangeColor(delta, isDark, resting)) // instant pop to the tick color
        color.animateTo(resting, tween(700))                       // then decay back to rest
        caret = 0                                                  // clear the arrow once the pulse settles
        // Note: a faster-than-700ms tick cancels this coroutine before the two lines above finish, so
        // the arrow keeps showing direction during a burst and only clears once ticks pause.
    }
    // Heal the resting color after a theme switch that lands while no tick is animating.
    LaunchedEffect(resting) {
        if (!color.isRunning) color.snapTo(resting)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(Res.string.portfolio_mark),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (caret != 0) {
            Icon(
                imageVector = if (caret > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = color.value,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = markPx.toPriceOrDash(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color.value,
        )
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
            text = "${stringResource(Res.string.portfolio_liq)} ${liqPx.toPriceValue()}  ·  ${awayPct.toPercent(1)}% ${stringResource(Res.string.portfolio_away)}",
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
            CoinIcon(
                baseAsset = row.coin.substringAfterLast(':'),
                modifier = Modifier.size(40.dp),
                fallback = { CoinMonogramFallback(row.coin, accent) },
            )
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
                row.sourceLabel?.let { WalletTagLine(it) }
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

/** Accent-tinted 2-letter monogram used as the [CoinIcon] fallback (network image failed / unknown symbol). */
@Composable
private fun CoinMonogramFallback(coin: String, accent: Color) {
    // Alt-dex coins are namespaced ("xyz:TSLA"); use the symbol after the prefix for the monogram.
    val symbol = coin.substringAfterLast(':')
    Box(
        modifier = Modifier.fillMaxSize().clip(CircleShape).background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol.take(if (symbol.length >= 2) 2 else 1).uppercase(),
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

/** Small wallet-attribution pill shown on each row in the aggregate "All" view. */
@Composable
private fun WalletTagLine(label: String) {
    Spacer(Modifier.height(3.dp))
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

    /** Index-based color for allocation slices, so distinct slices get distinct colors (for index < size). */
    fun colorAt(index: Int): Color = colors[index % colors.size]
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
    return "$sign${abs(this).toPercent(2)}%"
}

/** Rounds to [decimals] places and trims trailing zeros (e.g. 18.50 -> "18.5", 18.00 -> "18"). */
private fun Double.toPercent(decimals: Int): String {
    val factor = if (decimals == 1) 10.0 else 100.0
    val rounded = (this * factor).roundToLong() / factor
    val asLong = rounded.toLong()
    return if (rounded == asLong.toDouble()) asLong.toString()
    else rounded.toString().trimEnd('0').trimEnd('.')
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
