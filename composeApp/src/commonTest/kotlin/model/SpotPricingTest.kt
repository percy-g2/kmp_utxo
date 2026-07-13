package model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit tests for the pure spot-pricing helpers in SpotPricing.kt. */
class SpotPricingTest {

    private fun pair(base: Int, quote: Int, name: String, canonical: Boolean = false) =
        HlSpotPair(tokens = listOf(base, quote), name = name, isCanonical = canonical)

    // --- buildSpotTokenPairMap ---

    @Test
    fun maps_only_usdc_quoted_markets_keyed_by_base_token() {
        val map = buildSpotTokenPairMap(
            listOf(
                pair(base = 150, quote = 0, name = "@50"),          // USDC-quoted -> kept
                pair(base = 1, quote = 0, name = "PURR/USDC", canonical = true), // canonical name kept verbatim
                pair(base = 200, quote = 5, name = "@99"),          // non-USDC quote -> excluded
            ),
        )
        assertEquals("@50", map[150])
        assertEquals("PURR/USDC", map[1]) // uses the market name, not "@index"
        assertFalse(map.containsKey(200)) // non-USDC-quoted excluded
    }

    @Test
    fun canonical_market_wins_when_a_token_has_multiple_usdc_pairs() {
        // Order deliberately puts the non-canonical pair first to prove sorting, not input order, decides.
        val map = buildSpotTokenPairMap(
            listOf(
                pair(base = 150, quote = 0, name = "@50"),
                pair(base = 150, quote = 0, name = "HYPE/USDC", canonical = true),
            ),
        )
        assertEquals("HYPE/USDC", map[150])
    }

    @Test
    fun ignores_malformed_or_unnamed_pairs() {
        val map = buildSpotTokenPairMap(
            listOf(
                HlSpotPair(tokens = listOf(7), name = "@1"),   // too few token indices
                pair(base = 8, quote = 0, name = ""),          // blank name
            ),
        )
        assertTrue(map.isEmpty())
    }

    // --- spotTokenPrices ---

    @Test
    fun live_mid_overrides_seed_and_seed_fills_the_gap() {
        val pairs = mapOf(150 to "HYPE/USDC", 1 to "PURR/USDC")
        val prices = spotTokenPrices(
            pairs = pairs,
            mids = mapOf("HYPE/USDC" to 13.9),                 // live only for HYPE
            seed = mapOf("HYPE/USDC" to 10.0, "PURR/USDC" to 0.08),
        )
        assertEquals(13.9, prices[150]) // live mid wins over the 10.0 seed
        assertEquals(0.08, prices[1])   // seed fallback where no live mid exists
    }

    @Test
    fun non_positive_or_non_finite_prices_are_dropped() {
        val pairs = mapOf(1 to "A", 2 to "B", 3 to "C", 4 to "D")
        val prices = spotTokenPrices(
            pairs = pairs,
            mids = mapOf(
                "A" to 0.0,
                "B" to -1.0,
                "C" to Double.NaN,
                "D" to Double.POSITIVE_INFINITY,
            ),
        )
        assertTrue(prices.isEmpty()) // all suppressed -> tokens stay unpriced (usdValue null)
    }

    @Test
    fun token_with_no_market_is_absent() {
        val prices = spotTokenPrices(pairs = mapOf(9 to "@9"), mids = emptyMap(), seed = emptyMap())
        assertNull(prices[9])
    }
}
