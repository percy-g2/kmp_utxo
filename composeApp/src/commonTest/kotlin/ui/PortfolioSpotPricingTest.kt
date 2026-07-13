package ui

import model.HlSpotBalance
import model.HlSpotState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for live spot-holding valuation in [buildWalletData]: non-stablecoin spot balances are
 * priced from the shared `tokenIndex -> USD` map (fed by Hyperliquid's `allMids`). Verifies the
 * priced/unpriced/stablecoin branches and that valuation is a pure function of the price map (which
 * is what lets every mids tick recompute the total reactively).
 */
class PortfolioSpotPricingTest {

    private val eps = 1e-6

    // Pure-spot wallet (perp = null) keeps the focus on spot pricing, away from collateral netting.
    private fun walletWith(spot: HlSpotState?, prices: Map<Int, Double>) = buildWalletData(
        address = "0xspot",
        perp = null,
        spot = spot,
        altPerps = emptyList(),
        spotPrices = prices,
        perpMarks = emptyMap(),
        snapErr = false,
        isStale = false,
    )

    private fun spotState(vararg balances: HlSpotBalance) = HlSpotState(balances = balances.toList(), time = 1L)

    private fun bal(coin: String, total: String, hold: String = "0", token: Int) =
        HlSpotBalance(coin = coin, token = token, total = total, hold = hold)

    @Test
    fun priced_altcoin_contributes_its_market_value() {
        val data = walletWith(
            spot = spotState(bal(coin = "HYPE", total = "10", token = 150)),
            prices = mapOf(150 to 13.9),
        )
        val row = data.spot.single { it.coin == "HYPE" }
        assertEquals(139.0, row.usdValue!!, eps) // 10 * 13.9
        assertEquals(139.0, data.summary.totalValue, eps)
    }

    @Test
    fun altcoin_without_a_price_is_listed_but_excluded_from_total() {
        val data = walletWith(
            spot = spotState(bal(coin = "HYPE", total = "10", token = 150)),
            prices = emptyMap(), // no price for token 150
        )
        val row = data.spot.single { it.coin == "HYPE" }
        assertNull(row.usdValue)               // shown as an unpriced holding
        assertEquals(0.0, data.summary.totalValue, eps) // but contributes nothing
    }

    @Test
    fun stablecoin_ignores_the_price_map_and_uses_available_balance() {
        val data = walletWith(
            spot = spotState(bal(coin = "USDT", total = "50", hold = "0", token = 268)),
            prices = mapOf(268 to 999.0), // bogus price must be ignored for a stablecoin
        )
        val row = data.spot.single { it.coin == "USDT" }
        assertEquals(50.0, row.usdValue!!, eps) // $1 * available, not 50 * 999
        assertEquals(50.0, data.summary.totalValue, eps)
    }

    @Test
    fun altcoin_is_valued_on_full_total_including_held_balance() {
        val data = walletWith(
            // 10 total, 4 locked in an open spot order -> still owned.
            spot = spotState(bal(coin = "HYPE", total = "10", hold = "4", token = 150)),
            prices = mapOf(150 to 2.0),
        )
        val row = data.spot.single { it.coin == "HYPE" }
        assertEquals(20.0, row.usdValue!!, eps) // 10 * 2.0 (full total), not 6 * 2.0 (available)
    }

    @Test
    fun valuation_recomputes_when_the_price_map_changes() {
        val spot = spotState(bal(coin = "HYPE", total = "10", token = 150))
        val before = walletWith(spot = spot, prices = mapOf(150 to 10.0))
        val after = walletWith(spot = spot, prices = mapOf(150 to 12.5))
        assertEquals(100.0, before.summary.totalValue, eps)
        assertEquals(125.0, after.summary.totalValue, eps)
        assertTrue(after.summary.totalValue > before.summary.totalValue)
    }
}
