package ui

import model.HlAssetPosition
import model.HlMarginSummary
import model.HlPerpPosition
import model.HlPerpState
import model.HlSpotBalance
import model.HlSpotState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for multi-dex (HIP-3) aggregation in [buildWalletData]: positions on builder-deployed
 * alt dexes are unioned with the main dex, and per-dex collateral (isolated) is summed without
 * double-counting. Live API facts verified by curl: `perpDexs` lists alt dexes (xyz, flx, …); a
 * per-dex `clearinghouseState` has its own accountValue (deployer: 10.0 on main, 9.99 on xyz);
 * alt-dex asset coins are namespaced ("xyz:TSLA").
 */
class PortfolioMultiDexTest {

    private val eps = 1e-6

    private fun perpState(
        accountValue: String,
        marginUsed: String = "0",
        withdrawable: String = "0",
        positions: List<HlAssetPosition> = emptyList(),
    ) = HlPerpState(
        marginSummary = HlMarginSummary(accountValue = accountValue, totalMarginUsed = marginUsed),
        withdrawable = withdrawable,
        assetPositions = positions,
        time = 1L,
    )

    private fun position(coin: String, szi: String, positionValue: String, pnl: String = "0") =
        HlAssetPosition(
            type = "oneWay",
            position = HlPerpPosition(coin = coin, szi = szi, positionValue = positionValue, unrealizedPnl = pnl),
        )

    private fun spotState(vararg balances: HlSpotBalance) = HlSpotState(balances = balances.toList(), time = 1L)

    @Test
    fun alt_dex_positions_are_unioned_and_collateral_is_summed() {
        val data = buildWalletData(
            address = "0xmulti",
            perp = perpState(
                accountValue = "500.0",
                marginUsed = "100.0",
                withdrawable = "400.0",
                positions = listOf(position("BTC", "-0.04", "2600.0", pnl = "10.0")),
            ),
            spot = spotState(HlSpotBalance(coin = "USDC", token = 0, total = "500.0", hold = "100.0")),
            altPerps = listOf(
                perpState(
                    accountValue = "100.0",
                    marginUsed = "20.0",
                    withdrawable = "80.0",
                    positions = listOf(position("xyz:TSLA", "10", "5000.0", pnl = "5.0")),
                ),
            ),
            snapErr = false,
            isStale = false,
        )

        // accountValue/margin/withdrawable are summed across the main + alt dex.
        assertEquals(600.0, data.summary.accountValue, eps)
        assertEquals(120.0, data.summary.totalMarginUsed, eps)
        assertEquals(480.0, data.summary.withdrawable, eps)
        // Collateral USDC excluded (main account exists) -> total == summed equity, no double-count.
        assertEquals(600.0, data.summary.totalValue, eps)
        assertEquals(15.0, data.summary.totalUnrealizedPnl, eps) // 10 (BTC) + 5 (xyz:TSLA)
        // Both positions present; the larger alt-dex notional sorts first.
        assertEquals(2, data.perps.size)
        assertEquals("xyz:TSLA", data.perps.first().coin)
        assertTrue(data.perps.any { it.coin == "BTC" })
    }

    @Test
    fun alt_dex_equity_without_positions_still_counts() {
        // The xyz feeRecipient case: large equity parked on an alt dex with no open position.
        val data = buildWalletData(
            address = "0xparked",
            perp = perpState(accountValue = "500.0", positions = listOf(position("BTC", "-0.04", "2600.0"))),
            spot = spotState(HlSpotBalance(coin = "USDC", token = 0, total = "500.0", hold = "0.0")),
            altPerps = listOf(perpState(accountValue = "200.0")), // collateral, no positions
            snapErr = false,
            isStale = false,
        )
        assertEquals(700.0, data.summary.accountValue, eps)
        assertEquals(700.0, data.summary.totalValue, eps)
        assertEquals(1, data.perps.size) // only the main-dex BTC position
    }

    @Test
    fun no_alt_dexes_matches_main_only_behavior() {
        val data = buildWalletData(
            address = "0xmain",
            perp = perpState(accountValue = "535.78125", marginUsed = "519.395656", withdrawable = "16.385594"),
            spot = spotState(HlSpotBalance(coin = "USDC", token = 0, total = "535.78125052", hold = "519.395656")),
            altPerps = emptyList(),
            snapErr = false,
            isStale = false,
        )
        assertEquals(535.78125, data.summary.totalValue, eps)
        assertEquals(535.78125, data.summary.accountValue, eps)
    }

    @Test
    fun alt_dex_only_wallet_has_data() {
        // No main perp/spot, only an alt-dex account — must still surface as data, not empty.
        val data = buildWalletData(
            address = "0xaltonly",
            perp = null,
            spot = null,
            altPerps = listOf(perpState(accountValue = "100.0", positions = listOf(position("flx:GOLD", "1", "1000.0")))),
            snapErr = false,
            isStale = false,
        )
        assertTrue(data.hasData)
        assertEquals(100.0, data.summary.accountValue, eps)
        assertEquals(1, data.perps.size)
        assertEquals("flx:GOLD", data.perps.first().coin)
    }
}
