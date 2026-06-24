package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Hyperliquid portfolio models.
 *
 * The portfolio is read-only: it is derived entirely from a user's PUBLIC wallet
 * address (a 0x… EVM address). No private key or API secret is required or stored.
 *
 * HTTP info endpoint: POST https://api.hyperliquid.xyz/info
 *   - {"type":"clearinghouseState","user":"0x…"}      -> [HlPerpState]
 *   - {"type":"spotClearinghouseState","user":"0x…"}  -> [HlSpotState]
 *
 * WebSocket: wss://api.hyperliquid.xyz/ws
 *   - subscribe {"type":"webData3","user":"0x…"}  -> perps clearinghouseState (live)
 *   - subscribe {"type":"spotState","user":"0x…"} -> spot balances (live)
 *
 * Every field has a default so partial/empty responses (and webData3 schema drift)
 * never throw — the Json parser is configured with ignoreUnknownKeys = true.
 */

// region --- Raw API DTOs (perps) ---

@Serializable
data class HlMarginSummary(
    @SerialName("accountValue") val accountValue: String = "0",
    @SerialName("totalNtlPos") val totalNtlPos: String = "0",
    @SerialName("totalRawUsd") val totalRawUsd: String = "0",
    @SerialName("totalMarginUsed") val totalMarginUsed: String = "0",
)

@Serializable
data class HlLeverage(
    @SerialName("type") val type: String = "",   // "cross" | "isolated"
    @SerialName("value") val value: Int = 0,
)

@Serializable
data class HlPerpPosition(
    @SerialName("coin") val coin: String = "",
    @SerialName("szi") val szi: String = "0",            // signed size; leading "-" => short
    @SerialName("entryPx") val entryPx: String? = null,
    @SerialName("positionValue") val positionValue: String = "0",
    @SerialName("unrealizedPnl") val unrealizedPnl: String = "0",
    @SerialName("leverage") val leverage: HlLeverage = HlLeverage(),
    @SerialName("liquidationPx") val liquidationPx: String? = null,
    @SerialName("marginUsed") val marginUsed: String = "0",
    @SerialName("returnOnEquity") val returnOnEquity: String = "0",
)

@Serializable
data class HlAssetPosition(
    @SerialName("type") val type: String = "",
    @SerialName("position") val position: HlPerpPosition = HlPerpPosition(),
)

@Serializable
data class HlPerpState(
    @SerialName("marginSummary") val marginSummary: HlMarginSummary = HlMarginSummary(),
    @SerialName("crossMarginSummary") val crossMarginSummary: HlMarginSummary = HlMarginSummary(),
    @SerialName("withdrawable") val withdrawable: String = "0",
    @SerialName("assetPositions") val assetPositions: List<HlAssetPosition> = emptyList(),
    @SerialName("time") val time: Long = 0,
)

// endregion

// region --- Raw API DTOs (spot) ---

@Serializable
data class HlSpotBalance(
    @SerialName("coin") val coin: String = "",
    @SerialName("token") val token: Int = 0,
    @SerialName("total") val total: String = "0",
    @SerialName("hold") val hold: String = "0",
    @SerialName("entryNtl") val entryNtl: String? = null,
)

@Serializable
data class HlSpotState(
    @SerialName("balances") val balances: List<HlSpotBalance> = emptyList(),
    @SerialName("time") val time: Long = 0,
)

// endregion

// region --- UI / domain models consumed by the screen ---

data class PortfolioSummary(
    /** Best-effort total = perp equity + USD-stable spot value. */
    val totalValue: Double,
    val accountValue: Double,
    val totalUnrealizedPnl: Double,
    val totalMarginUsed: Double,
    val withdrawable: Double,
    /** Unrealized PnL as a % of cost-basis equity; null when not meaningful. */
    val pnlPercent: Double?,
)

data class PerpPositionRow(
    val coin: String,
    val isLong: Boolean,
    val size: Double,
    val entryPx: Double?,
    val markPx: Double?,
    /** Absolute notional USD value of the position (for allocation). */
    val notionalUsd: Double,
    val unrealizedPnl: Double,
    val roePercent: Double,
    val leverageText: String,
    val liquidationPx: Double?,
    /** Distance from mark to liquidation as a fraction (0 = at liq, 1 = far); null if no liq. */
    val liqDistanceFraction: Double?,
    val marginUsed: Double,
)

data class SpotBalanceRow(
    val coin: String,
    val total: Double,
    val available: Double,
    val hold: Double,
    /** USD value when known (stablecoins); null otherwise. */
    val usdValue: Double?,
)

sealed interface PortfolioUiState {
    /** Settings not yet loaded — transient. */
    data object Loading : PortfolioUiState

    /** No wallet address configured. */
    data object NoAddress : PortfolioUiState

    /** Configured address is not a valid 0x… EVM address. */
    data object InvalidAddress : PortfolioUiState

    /** Network/parse failure with no data to fall back on. */
    data object Error : PortfolioUiState

    data class Data(
        val summary: PortfolioSummary,
        val perpPositions: List<PerpPositionRow>,
        val spotBalances: List<SpotBalanceRow>,
        /** WebSocket dropped; showing last-known snapshot. */
        val isStale: Boolean = false,
    ) : PortfolioUiState
}

// endregion
