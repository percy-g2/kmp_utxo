package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.HlPerpPosition
import model.HlPerpState
import model.HlSpotBalance
import model.HlSpotState
import model.PerpPositionRow
import model.PortfolioSummary
import model.PortfolioUiState
import model.SpotBalanceRow
import network.HyperliquidService
import network.PortfolioWebSocketService
import theme.ThemeManager
import kotlin.math.abs

/**
 * Drives the Portfolio screen for a single public Hyperliquid wallet address.
 *
 * The address is observed from the persisted [Settings] store rather than passed in,
 * so editing it in Settings re-targets the live view without recreating the screen.
 * On a valid address it fetches an immediate HTTP snapshot and opens the live
 * WebSocket; the two raw sources are merged into a single [PortfolioUiState].
 */
class PortfolioViewModel : ViewModel() {
    private val httpService = HyperliquidService()
    private val wsService = PortfolioWebSocketService()
    private val store = ThemeManager.store

    private val addressFlow = MutableStateFlow<String?>(null) // null = settings not loaded yet
    private val rawPerp = MutableStateFlow<HlPerpState?>(null)
    private val rawSpot = MutableStateFlow<HlSpotState?>(null)
    private val snapshotError = MutableStateFlow(false)

    private var currentAddress: String? = null
    private var snapshotJob: Job? = null

    val state: StateFlow<PortfolioUiState> =
        combine(
            addressFlow,
            rawPerp,
            rawSpot,
            snapshotError,
            wsService.connectionError,
        ) { addr, perp, spot, snapErr, wsErr ->
            buildState(addr, perp, spot, snapErr, wsErr)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PortfolioUiState.Loading)

    init {
        // React to the saved wallet address changing in Settings.
        viewModelScope.launch {
            store.updates
                .map { it?.hyperliquidWalletAddress?.trim().orEmpty() }
                .distinctUntilChanged()
                .collect { onAddressChanged(it) }
        }
        // Feed live WS updates into the raw flows (ignore the null resets a disconnect emits).
        viewModelScope.launch { wsService.perpState.collect { if (it != null) rawPerp.value = it } }
        viewModelScope.launch { wsService.spotState.collect { if (it != null) rawSpot.value = it } }
    }

    private fun onAddressChanged(addr: String) {
        currentAddress = addr
        addressFlow.value = addr
        snapshotJob?.cancel()
        rawPerp.value = null
        rawSpot.value = null
        snapshotError.value = false

        if (!isValidHyperliquidAddress(addr)) {
            wsService.disconnect()
            return
        }
        loadSnapshot(addr)
        wsService.connect(addr)
    }

    private fun loadSnapshot(addr: String) {
        snapshotJob = viewModelScope.launch(Dispatchers.Default) {
            val perp = httpService.fetchPerpState(addr)
            val spot = httpService.fetchSpotState(addr)
            withContext(Dispatchers.Main) {
                if (perp == null && spot == null) {
                    snapshotError.value = true
                } else {
                    snapshotError.value = false
                    perp?.let { rawPerp.value = it }
                    spot?.let { rawSpot.value = it }
                }
            }
        }
    }

    /** Manual refresh (pull-to-refresh / retry). Live WS keeps the view fresh otherwise. */
    fun refresh() {
        currentAddress?.let { if (isValidHyperliquidAddress(it)) loadSnapshot(it) }
    }

    /** Screen became visible — (re)open the live connection. Safe to call repeatedly. */
    fun resume() {
        currentAddress?.let {
            if (isValidHyperliquidAddress(it)) {
                loadSnapshot(it)
                wsService.connect(it)
            }
        }
    }

    /** Screen hidden — drop the socket to save battery/data; last-known data is kept. */
    fun pause() {
        wsService.disconnect()
    }

    private fun buildState(
        addr: String?,
        perp: HlPerpState?,
        spot: HlSpotState?,
        snapErr: Boolean,
        wsErr: String?,
    ): PortfolioUiState {
        if (addr == null) return PortfolioUiState.Loading
        if (addr.isBlank()) return PortfolioUiState.NoAddress
        if (!isValidHyperliquidAddress(addr)) return PortfolioUiState.InvalidAddress
        if (perp == null && spot == null) {
            return if (snapErr) PortfolioUiState.Error else PortfolioUiState.Loading
        }

        val positions = perp?.assetPositions
            ?.map { it.position }
            ?.filter { it.coin.isNotBlank() && (it.szi.toDoubleOrNull() ?: 0.0) != 0.0 }
            ?.map { it.toRow() }
            ?.sortedByDescending { it.notionalUsd }
            .orEmpty()

        val balances = spot?.balances
            ?.map { it.toRow() }
            // Drop fully on-hold stablecoins: that USDC is perp collateral already
            // counted in accountValue (webData2 reports it as 0; the HTTP spot endpoint
            // returns it on-hold). usdValue is 0 only for a stablecoin with no available
            // balance; non-stable tokens keep a null usdValue and stay visible.
            ?.filter { it.total > 0.0 && it.usdValue != 0.0 }
            ?.sortedByDescending { it.usdValue ?: 0.0 }
            .orEmpty()

        val accountValue = perp?.marginSummary?.accountValue?.toDoubleOrNull() ?: 0.0
        val totalPnl = positions.sumOf { it.unrealizedPnl }
        val spotUsd = balances.sumOf { it.usdValue ?: 0.0 }
        val costBasis = accountValue - totalPnl
        val summary = PortfolioSummary(
            totalValue = accountValue + spotUsd,
            accountValue = accountValue,
            totalUnrealizedPnl = totalPnl,
            totalMarginUsed = perp?.marginSummary?.totalMarginUsed?.toDoubleOrNull() ?: 0.0,
            withdrawable = perp?.withdrawable?.toDoubleOrNull() ?: 0.0,
            pnlPercent = if (costBasis > 0.0) totalPnl / costBasis * 100.0 else null,
        )

        return PortfolioUiState.Data(
            summary = summary,
            perpPositions = positions,
            spotBalances = balances,
            isStale = wsErr != null,
        )
    }

    override fun onCleared() {
        super.onCleared()
        snapshotJob?.cancel()
        wsService.close()
        httpService.close()
    }
}

/** Spot coins treated as $1 USD for best-effort valuation (no price feed needed). */
private val STABLECOINS = setOf("USDC", "USDT", "USDT0", "USDE", "USDH", "USD1", "DAI", "USDB", "FDUSD")

private fun HlPerpPosition.toRow(): PerpPositionRow {
    val sziValue = szi.toDoubleOrNull() ?: 0.0
    val sizeAbs = abs(sziValue)
    val posValue = positionValue.toDoubleOrNull() ?: 0.0
    val mark = if (sizeAbs != 0.0) posValue / sizeAbs else null
    val entry = entryPx?.toDoubleOrNull()
    val liq = liquidationPx?.toDoubleOrNull()
    val roe = (returnOnEquity.toDoubleOrNull() ?: 0.0) * 100.0
    // Health: how far mark is from liquidation, normalized against the entry→liq span.
    val liqDistance = if (liq != null && mark != null && entry != null && entry != liq) {
        ((mark - liq) / (entry - liq)).coerceIn(0.0, 1.0)
    } else {
        null
    }
    return PerpPositionRow(
        coin = coin,
        isLong = sziValue >= 0.0,
        size = sizeAbs,
        entryPx = entry,
        markPx = mark,
        notionalUsd = abs(posValue),
        unrealizedPnl = unrealizedPnl.toDoubleOrNull() ?: 0.0,
        roePercent = roe,
        leverageText = if (leverage.value > 0) "${leverage.value}x ${leverage.type}".trim() else leverage.type,
        liquidationPx = liq,
        liqDistanceFraction = liqDistance,
        marginUsed = marginUsed.toDoubleOrNull() ?: 0.0,
    )
}

private fun HlSpotBalance.toRow(): SpotBalanceRow {
    val totalValue = total.toDoubleOrNull() ?: 0.0
    val holdValue = hold.toDoubleOrNull() ?: 0.0
    val availableValue = (totalValue - holdValue).coerceAtLeast(0.0)
    return SpotBalanceRow(
        coin = coin,
        total = totalValue,
        available = availableValue,
        hold = holdValue,
        // Value stablecoins by their AVAILABLE (free) balance — on-hold USDC is perp
        // collateral already reflected in accountValue, so counting total double-counts it.
        usdValue = if (coin.uppercase() in STABLECOINS) availableValue else null,
    )
}
