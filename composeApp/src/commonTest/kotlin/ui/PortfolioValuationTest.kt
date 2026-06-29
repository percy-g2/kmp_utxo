package ui

import model.HlAssetPosition
import model.HlMarginSummary
import model.HlPerpPosition
import model.HlPerpState
import model.HlSpotBalance
import model.HlSpotState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the spot-USDC cross-collateral netting in [buildWalletData].
 *
 * Ground truth from the live Hyperliquid API for 0x5277d597151bad688f442949ff4bf7e06075ebfb:
 *   portfolio.accountValue == perp accountValue == spot USDC total == 535.78125,
 *   spot USDC hold == perp totalMarginUsed == 519.395656,
 *   spot USDC available == perp withdrawable == 16.385594.
 * The whole spot USDC is the perp equity, so the correct Portfolio Value is 535.78 — NOT
 * 535.78 + 16.39 = 552.17 (the old double-count).
 */
class PortfolioValuationTest {

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

    private fun spotState(vararg balances: HlSpotBalance) = HlSpotState(balances = balances.toList(), time = 1L)

    private fun bal(coin: String, total: String, hold: String = "0", token: Int = 0) =
        HlSpotBalance(coin = coin, token = token, total = total, hold = hold)

    private val btcShort = HlAssetPosition(
        type = "oneWay",
        position = HlPerpPosition(
            coin = "BTC",
            szi = "-0.04339",
            entryPx = "60188.0",
            positionValue = "2596.97828",
            unrealizedPnl = "14.57904",
            marginUsed = "519.395656",
        ),
    )

    // --- isPerpCollateral ---

    @Test
    fun usdc_is_collateral_only_when_a_perp_account_exists() {
        assertTrue(isPerpCollateral("USDC", 535.78125))
        assertTrue(isPerpCollateral("usdc", 535.78125)) // case-insensitive
        assertFalse(isPerpCollateral("USDC", 0.0)) // pure-spot wallet
        assertFalse(isPerpCollateral("USDT", 535.78125)) // only the settlement currency
    }

    // --- buildWalletData: the real bug ---

    @Test
    fun unified_account_excludes_collateral_usdc_from_total() {
        val data = buildWalletData(
            address = "0x5277",
            perp = perpState(
                accountValue = "535.78125",
                marginUsed = "519.395656",
                withdrawable = "16.385594",
                positions = listOf(btcShort),
            ),
            spot = spotState(
                bal(coin = "USDC", total = "535.78125052", hold = "519.395656"),
                bal(coin = "USDE", total = "0.0", token = 235),
            ),
            altPerps = emptyList(),
            snapErr = false,
            isStale = false,
        )
        // Correct equity — the free USDC (16.39 == withdrawable) is NOT added on top.
        assertEquals(535.78125, data.summary.totalValue, eps)
        assertEquals(data.summary.accountValue, data.summary.totalValue, eps)
        assertEquals(16.385594, data.summary.withdrawable, eps)
        // Collateral USDC is not shown as a spot holding; the BTC short still renders.
        assertTrue(data.spot.none { it.coin == "USDC" })
        assertEquals(1, data.perps.size)
        assertEquals("BTC", data.perps.first().coin)
    }

    @Test
    fun old_double_counted_value_is_not_produced() {
        val data = buildWalletData(
            address = "0x5277",
            perp = perpState(accountValue = "535.78125", marginUsed = "519.395656", withdrawable = "16.385594"),
            spot = spotState(bal(coin = "USDC", total = "535.78125052", hold = "519.395656")),
            altPerps = emptyList(),
            snapErr = false,
            isStale = false,
        )
        assertFalse(data.summary.totalValue > 552.0) // would be 552.17 with the old `+ available` bug
    }

    // --- buildWalletData: cases that must still count spot value ---

    @Test
    fun pure_spot_wallet_counts_its_usdc() {
        val data = buildWalletData(
            address = "0xspot",
            perp = null, // no perp account
            spot = spotState(bal(coin = "USDC", total = "100.0", hold = "0.0")),
            altPerps = emptyList(),
            snapErr = false,
            isStale = false,
        )
        assertEquals(0.0, data.summary.accountValue, eps)
        assertEquals(100.0, data.summary.totalValue, eps)
        assertTrue(data.spot.any { it.coin == "USDC" })
    }

    @Test
    fun non_usdc_spot_stablecoin_is_still_counted_alongside_perp() {
        val data = buildWalletData(
            address = "0xmix",
            perp = perpState(accountValue = "500.0", marginUsed = "0.0", withdrawable = "500.0"),
            spot = spotState(
                bal(coin = "USDC", total = "500.0", hold = "0.0"), // collateral -> excluded
                bal(coin = "USDT", total = "50.0", hold = "0.0", token = 268), // genuine holding -> counted
            ),
            altPerps = emptyList(),
            snapErr = false,
            isStale = false,
        )
        assertEquals(550.0, data.summary.totalValue, eps)
        assertTrue(data.spot.none { it.coin == "USDC" })
        assertTrue(data.spot.any { it.coin == "USDT" })
    }
}
