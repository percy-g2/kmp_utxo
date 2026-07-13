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
 * Unit tests for unified-account valuation in [buildWalletData].
 *
 * Hyperliquid is a UNIFIED account: spot USDC is the perp collateral. The portion pledged to a
 * position is reported as spot `hold` (== perp `marginUsed`) and is already inside perp
 * `accountValue`; the free portion is spot `available`. So the correct total is
 * `accountValue + availableUSDC` (+ other spot) — the collateral is counted exactly once.
 *
 * Ground truth from the live API for 0x5277d597151bad688f442949ff4bf7e06075ebfb:
 *   perp accountValue = 320.68554, marginUsed = 318.502716, withdrawable = 2.182824;
 *   spot USDC total = 639.03148752, hold = 318.502716 (== marginUsed) -> available = 320.528771;
 *   Hyperliquid's reported total ≈ 641.2 (= accountValue + free USDC), NOT 320.69 (perp only).
 * The previous code dropped the ENTIRE spot USDC whenever a position existed, erasing the ~320 of
 * free USDC from the total ("the rest is not shown").
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

    private fun build(perp: HlPerpState?, spot: HlSpotState?) = buildWalletData(
        address = "0xtest",
        perp = perp,
        spot = spot,
        altPerps = emptyList(),
        spotPrices = emptyMap(),
        perpMarks = emptyMap(),
        snapErr = false,
        isStale = false,
    )

    private val btcShort = HlAssetPosition(
        type = "oneWay",
        position = HlPerpPosition(
            coin = "BTC",
            szi = "-0.04339",
            entryPx = "60188.0",
            positionValue = "2596.97828",
            unrealizedPnl = "14.57904",
            marginUsed = "318.502716",
        ),
    )

    @Test
    fun unified_account_counts_free_spot_usdc_on_top_of_perp_equity() {
        val data = build(
            perp = perpState(
                accountValue = "320.68554",
                marginUsed = "318.502716",
                withdrawable = "2.182824",
                positions = listOf(btcShort),
            ),
            // 639.03 total USDC, of which 318.50 is pledged as this position's collateral.
            spot = spotState(bal(coin = "USDC", total = "639.03148752", hold = "318.502716")),
        )
        // Perp equity (which already contains the pledged collateral) + the FREE USDC (320.53).
        assertEquals(320.68554 + 320.528771, data.summary.totalValue, 1e-4)
        assertEquals(320.68554, data.summary.accountValue, eps)
        assertEquals(2.182824, data.summary.withdrawable, eps)
        // The free USDC is now visible as a holding (it used to be erased); the BTC short still shows.
        assertEquals(320.528771, data.spot.single { it.coin == "USDC" }.usdValue!!, 1e-4)
        assertEquals(1, data.perps.size)
        assertEquals("BTC", data.perps.first().coin)
    }

    @Test
    fun fully_pledged_usdc_adds_nothing_and_is_hidden() {
        // Every USDC held as collateral (available == 0) -> total is just the perp equity, no USDC row.
        val data = build(
            perp = perpState(accountValue = "500.0", marginUsed = "500.0", withdrawable = "0.0"),
            spot = spotState(bal(coin = "USDC", total = "500.0", hold = "500.0")),
        )
        assertEquals(500.0, data.summary.totalValue, eps)
        assertTrue(data.spot.none { it.coin == "USDC" })
    }

    @Test
    fun pure_spot_wallet_counts_its_usdc() {
        val data = build(
            perp = null, // no perp account
            spot = spotState(bal(coin = "USDC", total = "100.0", hold = "0.0")),
        )
        assertEquals(0.0, data.summary.accountValue, eps)
        assertEquals(100.0, data.summary.totalValue, eps)
        assertTrue(data.spot.any { it.coin == "USDC" })
    }

    @Test
    fun non_usdc_spot_stablecoin_is_counted_alongside_perp() {
        val data = build(
            perp = perpState(accountValue = "500.0", marginUsed = "0.0", withdrawable = "500.0"),
            spot = spotState(
                bal(coin = "USDC", total = "500.0", hold = "0.0"),
                bal(coin = "USDT", total = "50.0", hold = "0.0", token = 268),
            ),
        )
        // accountValue 500 + free USDC 500 + USDT 50.
        assertEquals(1050.0, data.summary.totalValue, eps)
        assertTrue(data.spot.any { it.coin == "USDC" })
        assertTrue(data.spot.any { it.coin == "USDT" })
    }
}
