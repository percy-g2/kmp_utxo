package ui

import model.HlAssetPosition
import model.HlLeverage
import model.HlMarginSummary
import model.HlPerpPosition
import model.HlPerpState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for LIVE perp-position valuation in [buildWalletData]: between the (infrequent) webData2
 * account frames, a position's mark / value / PnL / ROE are recomputed from the live `allMids` mid
 * (`perpMarks`). Formulas verified against the Hyperliquid API:
 *   unrealizedPnl == szi * (mark - entry),  returnOnEquity == unrealizedPnl / (|szi| * entry / leverage).
 *
 * Fixture: a 0.5 BTC short, entry 60000, frame mark 59000 (positionValue 29500), leverage 20x cross.
 *   frame:  uPnl = -0.5*(59000-60000) =  500,  ROE = 500/(0.5*60000/20)=500/1500 = 33.33%
 *   @58000: uPnl = -0.5*(58000-60000) = 1000,  ROE = 1000/1500 = 66.67%,  notional = 0.5*58000 = 29000
 * Frame equity is 10500 (includes the frame's 500 PnL); at 58000 it should tick to 11000.
 */
class PortfolioPositionPricingTest {

    private val eps = 1e-6

    private val btcShort = HlAssetPosition(
        type = "oneWay",
        position = HlPerpPosition(
            coin = "BTC",
            szi = "-0.5",
            entryPx = "60000",
            positionValue = "29500",          // frame mark 59000 * 0.5
            unrealizedPnl = "500",            // -0.5 * (59000 - 60000)
            leverage = HlLeverage(type = "cross", value = 20),
            liquidationPx = "70000",
            marginUsed = "1500",
            returnOnEquity = "0.333333333",   // 500 / 1500
        ),
    )

    private fun wallet(perpMarks: Map<String, Double>) = buildWalletData(
        address = "0xperp",
        perp = HlPerpState(
            marginSummary = HlMarginSummary(accountValue = "10500", totalMarginUsed = "1500"),
            withdrawable = "9000",
            assetPositions = listOf(btcShort),
            time = 1L,
        ),
        spot = null,
        altPerps = emptyList(),
        spotPrices = emptyMap(),
        perpMarks = perpMarks,
        snapErr = false,
        isStale = false,
    )

    @Test
    fun live_mark_recomputes_position_value_pnl_and_roe() {
        val row = wallet(mapOf("BTC" to 58000.0)).perps.single()
        assertEquals(58000.0, row.markPx!!, eps)      // live mid, not the frame's 59000
        assertEquals(29000.0, row.notionalUsd, eps)   // 0.5 * 58000
        assertEquals(1000.0, row.unrealizedPnl, eps)  // -0.5 * (58000 - 60000)
        assertEquals(66.6666667, row.roePercent, 1e-4) // 1000 / 1500 * 100
    }

    @Test
    fun hero_equity_ticks_with_the_live_pnl_delta_but_cost_basis_stays_fixed() {
        val live = wallet(mapOf("BTC" to 58000.0))
        assertEquals(1000.0, live.summary.totalUnrealizedPnl, eps)
        assertEquals(11000.0, live.summary.accountValue, eps) // 10500 + (1000 - 500)
        assertEquals(11000.0, live.summary.totalValue, eps)
        assertEquals(10000.0, live.costBasis, eps)            // 10500 - 500, invariant to the live mark
    }

    @Test
    fun without_a_live_mark_the_authoritative_frame_values_are_used_unchanged() {
        val frame = wallet(emptyMap())
        val row = frame.perps.single()
        assertEquals(59000.0, row.markPx!!, eps)      // positionValue / size
        assertEquals(29500.0, row.notionalUsd, eps)
        assertEquals(500.0, row.unrealizedPnl, eps)   // exactly the reported webData2 PnL
        assertEquals(33.3333333, row.roePercent, 1e-4)
        assertEquals(10500.0, frame.summary.accountValue, eps) // no delta applied
        assertEquals(10000.0, frame.costBasis, eps)            // same stable cost basis
    }

    @Test
    fun a_mark_for_a_different_coin_does_not_affect_the_position() {
        // ETH mid present, BTC absent -> BTC position must fall back to its frame values.
        val row = wallet(mapOf("ETH" to 1772.0)).perps.single()
        assertEquals(59000.0, row.markPx!!, eps)
        assertEquals(500.0, row.unrealizedPnl, eps)
    }
}
