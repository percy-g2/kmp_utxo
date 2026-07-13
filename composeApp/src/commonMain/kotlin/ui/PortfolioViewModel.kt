package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.HlPerpPosition
import model.HlPerpState
import model.HlSpotBalance
import model.HlSpotState
import model.HyperliquidWallet
import model.MAX_WALLETS
import model.PerpPositionRow
import model.PortfolioSummary
import model.PortfolioUiState
import model.SCOPE_ALL
import model.SpotBalanceRow
import model.WalletData
import model.WalletTab
import model.displayName
import model.isValidHyperliquidAddress
import model.shortenAddress
import model.spotTokenPrices
import network.EnsResult
import network.EnsService
import network.HyperliquidMidsService
import network.HyperliquidService
import network.PortfolioWebSocketService
import theme.ThemeManager
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Drives the Portfolio screen for a set of public Hyperliquid wallet addresses.
 *
 * The wallet list and the selected scope are observed from the persisted [Settings] store,
 * so editing them in Settings re-targets the live view without recreating the screen. Each
 * tracked wallet gets its own [WalletStream] (HTTP snapshot + live WebSocket); the per-wallet
 * results are combined into either a single-wallet view or an aggregate "All" view depending
 * on [selectedScope]. [walletTabs] and [selectedScope] are exposed independently of [state]
 * so the scope switcher stays visible even while the body is Loading/Error.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class PortfolioViewModel : ViewModel() {
    private val httpService = HyperliquidService() // shared: one RateLimiter across all wallets
    private val ensService = EnsService()
    private val store = ThemeManager.store

    /** One shared, wallet-independent live mid-price feed used to value spot holdings. */
    private val midsService = HyperliquidMidsService()

    /** Static `tokenIndex -> USDC-market-name` map + bootstrap prices (from [HyperliquidService.spotMeta]). */
    private val spotTokenPairs = MutableStateFlow<Map<Int, String>>(emptyMap())
    private val spotSeedPrices = MutableStateFlow<Map<String, Double>>(emptyMap())

    /** Guards the one-shot spot-metadata fetch; reset only on failure so a later resume retries. */
    private var spotMetaLoaded = false

    /** Single-writer map of live streams (mutated only from the Main settings collector). */
    private val streams = mutableMapOf<String, WalletStream>()

    private val walletOrder = MutableStateFlow<List<String>>(emptyList())
    private val walletsMeta = MutableStateFlow<List<HyperliquidWallet>>(emptyList())
    private val selectedScopeState = MutableStateFlow(SCOPE_ALL)
    private val settingsLoaded = MutableStateFlow(false)

    /** True while the screen is visible — gates whether newly-added streams connect. */
    private var isActive = false

    /**
     * Addresses already attempted in this VM instance (mutated only on Main, never removed).
     * Covers both the in-flight window and completed attempts, so a failing ENS endpoint is
     * not re-hit on every settings emission; a fresh VM (next app launch) retries.
     */
    private val attemptedEnsAddresses = mutableSetOf<String>()

    val selectedScope: StateFlow<String> = selectedScopeState

    /**
     * The shared live-price inputs for valuation, both fed by the one [midsService] `allMids` feed:
     *  - [spotByToken]: `tokenIndex -> USD` for spot holdings (static token->market map joined with
     *    live mids, seeded by the HTTP snapshot).
     *  - [marksByCoin]: `coin -> mid`, the live perp mark used to tick position value/PnL/ROE between
     *    the (infrequent) clearinghouseState account frames.
     */
    private data class LivePrices(
        val spotByToken: Map<Int, Double>,
        val marksByCoin: Map<String, Double>,
    )

    /**
     * `sample`d to bound per-tick main-thread work under a high-rate mids feed; [StateFlow] then
     * dedups no-op emissions so identical ticks don't recompute wallet valuations.
     */
    @OptIn(FlowPreview::class)
    private val livePrices: StateFlow<LivePrices> =
        combine(spotTokenPairs, spotSeedPrices, midsService.mids) { pairs, seed, mids ->
            LivePrices(spotByToken = spotTokenPrices(pairs, mids, seed), marksByCoin = mids)
        }
            .sample(PRICE_SAMPLE_MS)
            .stateIn(viewModelScope, SharingStarted.Eagerly, LivePrices(emptyMap(), emptyMap()))

    val state: StateFlow<PortfolioUiState> =
        combine(walletOrder, selectedScopeState, walletsMeta, settingsLoaded) { order, scope, meta, loaded ->
            StateInputs(order, scope, meta, loaded)
        }.flatMapLatest { inp ->
            if (inp.order.isEmpty()) {
                flowOf(if (inp.loaded) PortfolioUiState.NoAddress else PortfolioUiState.Loading)
            } else {
                combine(inp.order.map { walletDataFlow(it) }) { it.toList() }
                    .map { data -> assembleState(data, inp.scope, inp.meta) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PortfolioUiState.Loading)

    val walletTabs: StateFlow<List<WalletTab>> =
        combine(walletOrder, walletsMeta) { order, meta -> order to meta }
            .flatMapLatest { (order, meta) ->
                if (order.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(order.map { addr -> streamStaleFlow(addr) }) { staleArr ->
                        buildList {
                            // "All" chip: key is the sentinel; its label is resolved in the UI.
                            add(WalletTab(SCOPE_ALL, "", staleArr.any { it }))
                            order.forEachIndexed { i, addr ->
                                val label = meta.find { it.address == addr }?.displayName() ?: shortenAddress(addr)
                                add(WalletTab(addr, label, staleArr[i]))
                            }
                        }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Single writer for the stream map + derived flows. Runs sequentially on Main.
        viewModelScope.launch(Dispatchers.Main) {
            store.updates.collect { settings ->
                val wallets = settings?.hyperliquidWallets ?: emptyList()
                walletsMeta.value = wallets
                selectedScopeState.value = settings?.effectivePortfolioScope() ?: SCOPE_ALL
                settingsLoaded.value = true

                val desired = wallets.map { it.address.lowercase() }
                    .filter { isValidHyperliquidAddress(it) }
                    .distinct()
                    .take(MAX_WALLETS)
                if (desired != walletOrder.value) syncStreams(desired)

                maybeResolveEns(wallets)
            }
        }
    }

    /** Diff the stream map against [desired]; close removed, create+start added; emit new order. */
    private fun syncStreams(desired: List<String>) {
        (streams.keys - desired.toSet()).toList().forEach { addr ->
            streams.remove(addr)?.close()
        }
        desired.forEach { addr ->
            if (!streams.containsKey(addr)) {
                val stream = WalletStream(addr, httpService, viewModelScope)
                streams[addr] = stream
                if (isActive) stream.resume()
            }
        }
        walletOrder.value = desired // emit only after the map is populated (avoids a flatMapLatest race)
    }

    /** Per-wallet raw inputs, bundled so the shared [livePrices] can join via a second combine. */
    private data class WalletRaw(
        val perp: HlPerpState?,
        val spot: HlSpotState?,
        val altPerp: List<HlPerpState>,
        val snapErr: Boolean,
        val wsErr: String?,
    )

    private fun walletDataFlow(address: String): Flow<WalletData> {
        val s = streams[address] ?: return flowOf(emptyWalletData(address))
        val raw = combine(s.rawPerp, s.rawSpot, s.rawAltPerp, s.snapshotError, s.connectionError) {
                perp, spot, altPerp, snapErr, wsErr ->
            WalletRaw(perp, spot, altPerp, snapErr, wsErr)
        }
        return combine(raw, livePrices) { r, lp ->
            buildWalletData(
                address, r.perp, r.spot, r.altPerp,
                lp.spotByToken, lp.marksByCoin, r.snapErr, isStale = r.wsErr != null,
            )
        }
    }

    private fun streamStaleFlow(address: String): Flow<Boolean> =
        streams[address]?.connectionError?.let { flow -> flow.map { it != null } } ?: flowOf(false)

    /** Manual refresh (pull-to-refresh / retry) — re-fetch the HTTP snapshot for every wallet. */
    fun refresh() {
        streams.values.forEach { it.refresh() }
    }

    /** Persist the selected scope: [SCOPE_ALL] or a single wallet address. */
    fun selectScope(scope: String) {
        viewModelScope.launch { selectPortfolioScope(scope) }
    }

    /** Screen became visible — (re)open every wallet's live connection + the shared price feed. */
    fun resume() {
        isActive = true
        loadSpotMetaOnce()
        midsService.connect()
        streams.values.forEach { it.resume() }
    }

    /** Screen hidden — drop sockets to save battery/data; last-known data (and mids) is kept. */
    fun pause() {
        isActive = false
        midsService.disconnect()
        streams.values.forEach { it.pause() }
    }

    /**
     * Fetch the static spot token->market map + bootstrap prices once. Runs off-Main; on failure the
     * guard is reset so the next [resume] retries. Spot valuation degrades gracefully to unpriced
     * (as before) until this lands, then the shared [livePrices] flow starts resolving USD values.
     */
    private fun loadSpotMetaOnce() {
        if (spotMetaLoaded) return
        spotMetaLoaded = true // read + written only on Main (single-writer), like wsDelivered/streams
        viewModelScope.launch(Dispatchers.Default) {
            val meta = httpService.spotMeta()
            withContext(Dispatchers.Main) {
                if (meta == null) {
                    spotMetaLoaded = false // allow the next resume() to retry
                } else {
                    spotTokenPairs.value = meta.tokenIndexToPair
                    spotSeedPrices.value = meta.seedPrices
                }
            }
        }
    }

    private fun assembleState(data: List<WalletData>, scope: String, meta: List<HyperliquidWallet>): PortfolioUiState {
        if (scope != SCOPE_ALL) {
            val wd = data.find { it.address == scope } ?: return PortfolioUiState.Loading
            return assembleSingle(wd)
        }
        return aggregate(data, meta)
    }

    private fun assembleSingle(wd: WalletData): PortfolioUiState =
        if (!wd.hasData) {
            if (wd.snapshotError) PortfolioUiState.Error else PortfolioUiState.Loading
        } else {
            PortfolioUiState.Data(wd.summary, wd.perps, wd.spot, isStale = wd.isStale)
        }

    private fun aggregate(data: List<WalletData>, meta: List<HyperliquidWallet>): PortfolioUiState {
        val withData = data.filter { it.hasData }
        if (withData.isEmpty()) {
            return if (data.isNotEmpty() && data.all { it.snapshotError }) {
                PortfolioUiState.Error
            } else {
                PortfolioUiState.Loading
            }
        }
        fun nameFor(addr: String) = meta.find { it.address == addr }?.displayName() ?: shortenAddress(addr)

        val totalPnl = withData.sumOf { it.summary.totalUnrealizedPnl }
        val costBasis = withData.sumOf { it.costBasis }
        val summary = PortfolioSummary(
            totalValue = withData.sumOf { it.summary.totalValue },
            accountValue = withData.sumOf { it.summary.accountValue },
            totalUnrealizedPnl = totalPnl,
            totalMarginUsed = withData.sumOf { it.summary.totalMarginUsed },
            withdrawable = withData.sumOf { it.summary.withdrawable },
            // Blend by summed cost basis — never average percentages.
            pnlPercent = if (costBasis > 0.0) totalPnl / costBasis * 100.0 else null,
        )
        val perps = withData
            .flatMap { wd -> wd.perps.map { it.copy(sourceLabel = nameFor(wd.address)) } }
            .sortedByDescending { it.notionalUsd }
        val spot = withData
            .flatMap { wd -> wd.spot.map { it.copy(sourceLabel = nameFor(wd.address)) } }
            .sortedByDescending { it.usdValue ?: 0.0 }
        // Stale if any wallet's socket is down, or some wallets haven't produced data yet.
        val isStale = data.any { it.isStale } || withData.size < data.size
        return PortfolioUiState.Data(summary, perps, spot, isStale = isStale)
    }

    /** Resolve ENS for wallets without a manual label and past the TTL; write the result back. */
    private fun maybeResolveEns(wallets: List<HyperliquidWallet>) {
        val now = Clock.System.now().toEpochMilliseconds()
        wallets.forEach { w ->
            if (w.customLabel != null) return@forEach
            val resolvedAt = w.ensResolvedAtMillis
            val due = resolvedAt == null || (now - resolvedAt) > ENS_TTL_MILLIS
            if (!due) return@forEach
            // Attempt each address at most once per VM instance (this runs on Main).
            if (!attemptedEnsAddresses.add(w.address)) return@forEach
            viewModelScope.launch(Dispatchers.Default) {
                when (val result = ensService.resolveName(w.address)) {
                    is EnsResult.Resolved -> cacheWalletEnsName(w.address, result.name, now)
                    EnsResult.Failed -> Unit // leave un-stamped; a future app launch retries
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        streams.values.forEach { it.close() }
        streams.clear()
        midsService.close()
        ensService.close()
        httpService.close()
    }

    private data class StateInputs(
        val order: List<String>,
        val scope: String,
        val meta: List<HyperliquidWallet>,
        val loaded: Boolean,
    )

    companion object {
        private const val ENS_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** Coalesce high-rate mid-price ticks so spot revaluation doesn't churn the main thread. */
        private const val PRICE_SAMPLE_MS = 500L
    }
}

/**
 * One wallet's live data source: an HTTP snapshot plus a dedicated [PortfolioWebSocketService].
 * Owns a child [CoroutineScope] cancelled in [close] so its collectors don't leak when the
 * wallet is removed mid-session.
 */
private class WalletStream(
    val address: String,
    private val httpService: HyperliquidService,
    parent: CoroutineScope,
) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val ws = PortfolioWebSocketService()

    val rawPerp = MutableStateFlow<HlPerpState?>(null)
    val rawSpot = MutableStateFlow<HlSpotState?>(null)

    /**
     * Perp state on builder-deployed (HIP-3) alt dexes. HTTP-only: the live clearinghouseState WS
     * carries the main dex only, so these are fetched separately and never touched by the WS collector.
     */
    val rawAltPerp = MutableStateFlow<List<HlPerpState>>(emptyList())
    val snapshotError = MutableStateFlow(false)
    val connectionError: StateFlow<String?> get() = ws.connectionError

    private var snapshotJob: Job? = null
    private var httpRefreshJob: Job? = null

    // Source-of-truth bookkeeping (single-writer: mutated only on Main).
    // The live clearinghouseState WS is authoritative for perp; the HTTP snapshot is bootstrap-only.
    private var wsDelivered = false
    private var perpFromWs = false
    private var spotFromWs = false

    /** Alt-dex states are fetched on first snapshot + explicit refresh only, to bound the fan-out. */
    private var altFetched = false

    init {
        // Feed live WS perp frames into rawPerp (ignore the null resets a disconnect emits).
        // A non-null clearinghouseState frame means the WS is delivering, so it owns perp state.
        // Spot has no live WS channel, so it is sourced entirely over HTTP (see loadSnapshot).
        scope.launch {
            ws.perpState.collect { if (it != null) { wsDelivered = true; applyPerp(it, fromWs = true) } }
        }
    }

    /** True while the perp WS is connected and has delivered — it then owns [rawPerp]. */
    private fun wsOwnsState(): Boolean = wsDelivered && ws.connectionError.value == null

    /** Apply a perp frame under source-priority + monotonic-time + partial-frame arbitration. */
    private fun applyPerp(incoming: HlPerpState, fromWs: Boolean) {
        if (!shouldApplyPerp(rawPerp.value, perpFromWs, incoming, fromWs)) return
        rawPerp.value = incoming
        perpFromWs = fromWs
    }

    /** Apply a spot frame under source-priority + monotonic-time arbitration. */
    private fun applySpot(incoming: HlSpotState, fromWs: Boolean) {
        if (!shouldApplySpot(rawSpot.value, spotFromWs, incoming, fromWs)) return
        rawSpot.value = incoming
        spotFromWs = fromWs
    }

    private fun loadSnapshot(force: Boolean = false) {
        val includeAlt = force || !altFetched
        snapshotJob?.cancel()
        snapshotJob = scope.launch(Dispatchers.Default) {
            val perp = httpService.fetchPerpState(address)
            val spot = httpService.fetchSpotState(address)
            withContext(Dispatchers.Main) {
                // Perp: the live clearinghouseState WS is authoritative once delivered; the HTTP
                // snapshot only bootstraps before the first frame and must not clobber a fresher WS
                // frame (shouldApplyPerp enforces WS-over-HTTP + monotonic time across reconnects).
                if (!wsOwnsState()) {
                    if (perp == null && spot == null) {
                        snapshotError.value = true
                    } else {
                        snapshotError.value = false
                        perp?.let { applyPerp(it, fromWs = false) }
                    }
                }
                // Spot has no live WS channel, so HTTP is its sole source — always apply it (the
                // monotonic-time guard in shouldApplySpot still drops out-of-order frames).
                spot?.let { applySpot(it, fromWs = false) }
            }
            // Alt (HIP-3) dexes are HTTP-only and secondary — the live WS never carries them. Fetch
            // them AFTER the main snapshot so the per-dex fan-out never delays the main data, and
            // commit regardless of wsOwnsState (the WS path doesn't own this flow).
            if (includeAlt) {
                val alt = httpService.fetchAltPerpStates(address)
                withContext(Dispatchers.Main) {
                    altFetched = true
                    rawAltPerp.value = alt
                }
            }
        }
    }

    fun refresh() = loadSnapshot(force = true)

    /**
     * Keep the HTTP-only data fresh while the screen is open. Neither spot balances nor alt-dex
     * (HIP-3) perps have a live WS channel, so they are re-fetched on an interval — unlike main perps,
     * which stream live via [ws]. Spot balances change only on trades/transfers (their USD value
     * ticks live via the separate mids feed), and each poll fans out one request per alt dex; all go
     * through the shared [HyperliquidService] rate limiter, so the cost stays bounded across wallets.
     * Empty alt results (position closed / no HIP-3 footprint) legitimately clear [rawAltPerp].
     */
    private fun startHttpRefresh() {
        httpRefreshJob?.cancel()
        httpRefreshJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(HTTP_REFRESH_MS)
                val spot = httpService.fetchSpotState(address)
                val alt = httpService.fetchAltPerpStates(address)
                withContext(Dispatchers.Main) {
                    spot?.let { applySpot(it, fromWs = false) }
                    altFetched = true
                    rawAltPerp.value = alt
                }
            }
        }
    }

    fun resume() {
        loadSnapshot()
        ws.connect(address)
        startHttpRefresh()
    }

    fun pause() {
        // Drop the "delivered" latch so the next resume can bootstrap from HTTP again if the
        // socket is slow; the last (WS-sourced) perp values are retained until a fresh frame
        // replaces them.
        wsDelivered = false
        ws.disconnect()
        httpRefreshJob?.cancel()
    }

    fun close() {
        snapshotJob?.cancel()
        httpRefreshJob?.cancel()
        ws.close()
        scope.cancel()
    }

    private companion object {
        /** Cadence for re-fetching the HTTP-only data (spot balances + HIP-3 alt dexes) while visible. */
        const val HTTP_REFRESH_MS = 20_000L
    }
}

// region --- Frame arbitration (pure, unit-tested) ---

/**
 * A partial/half-formed perp payload: it carries margin/equity but no positions. The live WS can
 * briefly emit such a frame; accepting it would blank out a real open position. Rejecting it keeps
 * the last good perp state until a consistent frame arrives.
 */
internal fun isPartialPerp(state: HlPerpState): Boolean =
    state.assetPositions.isEmpty() &&
        ((state.marginSummary.totalMarginUsed.toDoubleOrNull() ?: 0.0) > 0.0 ||
            (state.marginSummary.accountValue.toDoubleOrNull() ?: 0.0) > 0.0)

/**
 * Decide whether [incoming] perp frame should replace [current]. The live WS (clearinghouseState) is
 * the source of truth: a WS frame always supersedes an HTTP-sourced one, and an HTTP frame never
 * displaces a WS one. Within the same source, a strictly-older `time` is dropped (0 ⇒ unknown ⇒ accept).
 * Partial frames are always rejected.
 */
internal fun shouldApplyPerp(
    current: HlPerpState?,
    currentFromWs: Boolean,
    incoming: HlPerpState,
    incomingFromWs: Boolean,
): Boolean {
    if (isPartialPerp(incoming)) return false
    if (current == null) return true
    if (incomingFromWs != currentFromWs) return incomingFromWs // WS wins over HTTP, never the reverse
    if (incoming.time > 0L && current.time > 0L && incoming.time < current.time) return false
    return true
}

/**
 * Decide whether [incoming] spot frame should replace [current]. Same source-priority + monotonic
 * rules as [shouldApplyPerp]; spot has no "partial" notion (an empty balance list is legitimate).
 */
internal fun shouldApplySpot(
    current: HlSpotState?,
    currentFromWs: Boolean,
    incoming: HlSpotState,
    incomingFromWs: Boolean,
): Boolean {
    if (current == null) return true
    if (incomingFromWs != currentFromWs) return incomingFromWs
    if (incoming.time > 0L && current.time > 0L && incoming.time < current.time) return false
    return true
}

// endregion

/**
 * Stablecoins valued at $1 by their AVAILABLE (free) balance. For USDC in a unified account the free
 * part is the balance NOT pledged as perp collateral (the pledged `hold` is already in accountValue),
 * so this basis lets `accountValue + availableUSDC` count the collateral exactly once.
 */
private val STABLECOINS = setOf("USDC", "USDT", "USDT0", "USDE", "USDH", "USD1", "DAI", "USDB", "FDUSD")

private fun emptyWalletData(address: String): WalletData =
    WalletData(
        address = address,
        summary = PortfolioSummary(0.0, 0.0, 0.0, 0.0, 0.0, null),
        perps = emptyList(),
        spot = emptyList(),
        costBasis = 0.0,
        hasData = false,
        snapshotError = false,
        isStale = false,
    )

internal fun buildWalletData(
    address: String,
    perp: HlPerpState?,
    spot: HlSpotState?,
    altPerps: List<HlPerpState>,
    spotPrices: Map<Int, Double>,
    perpMarks: Map<String, Double>,
    snapErr: Boolean,
    isStale: Boolean,
): WalletData {
    // Positions across the main dex AND every builder-deployed (HIP-3) alt dex. Alt-dex coins are
    // namespaced (e.g. "xyz:TSLA"), so they are self-identifying and never collide with main coins.
    val perpStates = listOfNotNull(perp) + altPerps
    val rawPositions = perpStates
        .flatMap { it.assetPositions }
        .map { it.position }
        .filter { it.coin.isNotBlank() && (it.szi.toDoubleOrNull() ?: 0.0) != 0.0 }
    val positions = rawPositions
        .map { it.toRow(perpMarks) }
        .sortedByDescending { it.notionalUsd }

    // Collateral is isolated per dex, so per-dex accountValue/margin are additive across dexes (no
    // shared pool to double-count).
    // Equity moves 1:1 with unrealized PnL between account frames. `perpMarks` refreshes each
    // position's live PnL faster than clearinghouseState pushes a new accountValue, so carry that
    // delta onto the frame's equity — the hero total then ticks with the position cards and
    // re-converges to the exchange's accountValue when the next frame lands (delta -> 0 when no live mark).
    val framePnl = rawPositions.sumOf { it.unrealizedPnl.toDoubleOrNull() ?: 0.0 }
    val livePnl = positions.sumOf { it.unrealizedPnl }
    val accountValue = perpStates.sumOf { it.marginSummary.accountValue.toDoubleOrNull() ?: 0.0 } +
        (livePnl - framePnl)
    val totalMarginUsed = perpStates.sumOf { it.marginSummary.totalMarginUsed.toDoubleOrNull() ?: 0.0 }
    val withdrawable = perpStates.sumOf { it.withdrawable.toDoubleOrNull() ?: 0.0 }

    // Hyperliquid is a unified account: spot USDC is the perp collateral. The portion pledged to a
    // position is reported as spot `hold` (== perp marginUsed) and is already inside `accountValue`;
    // the free portion is spot `available`. Valuing USDC at its AVAILABLE balance (see toRow) and
    // adding it to accountValue therefore counts the collateral exactly once — `accountValue +
    // availableUSDC` == total spot equity + perp PnL. Previously the ENTIRE USDC balance was dropped
    // whenever a position existed, which erased the free portion from the total ("the rest").
    val balances = spot?.balances
        ?.map { it.toRow(spotPrices) }
        ?.filter { it.total > 0.0 && it.usdValue != 0.0 }
        ?.sortedByDescending { it.usdValue ?: 0.0 }
        .orEmpty()

    val totalPnl = livePnl
    val spotUsd = balances.sumOf { it.usdValue ?: 0.0 }
    // costBasis == frame accountValue - frame PnL (the live delta cancels), so it stays stable and
    // the blended PnL% is computed against the true cost basis, not the ticking equity.
    val costBasis = accountValue - totalPnl
    val summary = PortfolioSummary(
        totalValue = accountValue + spotUsd,
        accountValue = accountValue,
        totalUnrealizedPnl = totalPnl,
        totalMarginUsed = totalMarginUsed,
        withdrawable = withdrawable,
        pnlPercent = if (costBasis > 0.0) totalPnl / costBasis * 100.0 else null,
    )
    return WalletData(
        address = address,
        summary = summary,
        perps = positions,
        spot = balances,
        costBasis = costBasis,
        hasData = perp != null || spot != null || altPerps.isNotEmpty(),
        snapshotError = snapErr,
        isStale = isStale,
    )
}

private fun HlPerpPosition.toRow(perpMarks: Map<String, Double>): PerpPositionRow {
    val sziValue = szi.toDoubleOrNull() ?: 0.0
    val sizeAbs = abs(sziValue)
    val posValue = positionValue.toDoubleOrNull() ?: 0.0
    val entry = entryPx?.toDoubleOrNull()
    val liq = liquidationPx?.toDoubleOrNull()

    // clearinghouseState gives an implied mark (positionValue / size) only as fresh as the last frame.
    // The live allMids mid ticks continuously between frames, so prefer it — then derive value / PnL
    // / ROE from that mark. These are exact functions of the mark, verified against the API:
    //   unrealizedPnl == szi * (mark - entry);  returnOnEquity == unrealizedPnl / (|szi| * entry / leverage).
    // Using a fresher mark makes them tick live; on the next frame the implied mark catches up, so the
    // live figures re-converge to the exchange's. Alt-dex coins ("xyz:TSLA") aren't in the main-dex
    // allMids, so `liveMark` is null there and the authoritative frame values are used unchanged.
    val frameMark = if (sizeAbs != 0.0) posValue / sizeAbs else null
    val liveMark = perpMarks[coin]?.takeIf { it.isFinite() && it > 0.0 }
    val mark = liveMark ?: frameMark

    val notionalUsd = if (mark != null) sizeAbs * mark else abs(posValue)
    val uPnl = if (liveMark != null && entry != null) {
        sziValue * (liveMark - entry)
    } else {
        unrealizedPnl.toDoubleOrNull() ?: 0.0
    }
    val roe = if (liveMark != null && entry != null && leverage.value > 0 && sizeAbs != 0.0) {
        val initialMargin = sizeAbs * entry / leverage.value
        if (initialMargin > 0.0) uPnl / initialMargin * 100.0 else (returnOnEquity.toDoubleOrNull() ?: 0.0) * 100.0
    } else {
        (returnOnEquity.toDoubleOrNull() ?: 0.0) * 100.0
    }

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
        notionalUsd = notionalUsd,
        unrealizedPnl = uPnl,
        roePercent = roe,
        leverageText = if (leverage.value > 0) "${leverage.value}x ${leverage.type}".trim() else leverage.type,
        liquidationPx = liq,
        liqDistanceFraction = liqDistance,
        marginUsed = marginUsed.toDoubleOrNull() ?: 0.0,
    )
}

private fun HlSpotBalance.toRow(spotPrices: Map<Int, Double>): SpotBalanceRow {
    val totalValue = total.toDoubleOrNull() ?: 0.0
    val holdValue = hold.toDoubleOrNull() ?: 0.0
    val availableValue = (totalValue - holdValue).coerceAtLeast(0.0)
    val usd = when {
        // Value stablecoins by their AVAILABLE (free) balance. Collateral USDC is excluded entirely
        // upstream in buildWalletData (it is perp equity, not a spot holding), so this AVAILABLE
        // basis only applies to genuine spot stablecoins (non-USDC, or USDC on a pure-spot wallet).
        coin.uppercase() in STABLECOINS -> availableValue
        // Priced altcoin: value the FULL holding (incl. the `hold` locked in open spot orders — it is
        // still owned and never perp collateral). Absent price -> null: shown but out of the total.
        else -> spotPrices[token]?.let { totalValue * it }
    }
    return SpotBalanceRow(
        coin = coin,
        total = totalValue,
        available = availableValue,
        hold = holdValue,
        usdValue = usd,
    )
}
