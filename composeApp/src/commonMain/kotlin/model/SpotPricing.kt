package model

/**
 * Pure spot-pricing helpers, kept free of coroutines/network so they are trivially unit-testable.
 *
 * The portfolio values non-stablecoin spot holdings from Hyperliquid's own price feed:
 *   1. [buildSpotTokenPairMap] turns the static spot metadata (`spotMeta.universe`) into a
 *      `tokenIndex -> USDC-market-name` map.
 *   2. [spotTokenPrices] joins that static map with the live `allMids` feed (pair name -> mid) to
 *      produce `tokenIndex -> USD price`, which the valuation layer looks up by
 *      [HlSpotBalance.token].
 */

/**
 * base `tokenIndex -> USDC-quoted market name`, from `spotMeta.universe`. Only markets quoted in
 * USDC (quote token index 0) are usable for a direct USD valuation. When a base token has more than
 * one USDC market, the canonical one wins (sorted first, kept via `putIfAbsent`).
 */
fun buildSpotTokenPairMap(universe: List<HlSpotPair>): Map<Int, String> {
    val out = HashMap<Int, String>()
    universe.sortedByDescending { it.isCanonical }.forEach { p ->
        if (p.tokens.size == 2 && p.tokens[1] == 0 && p.name.isNotEmpty()) {
            // Canonical pairs sort first, so first-write-wins keeps the canonical name on duplicates.
            if (!out.containsKey(p.tokens[0])) out[p.tokens[0]] = p.name
        }
    }
    return out
}

/**
 * `tokenIndex -> USD price`. Live [mids] wins; [seed] (the HTTP `spotMetaAndAssetCtxs` snapshot)
 * fills the bootstrap gap before the live socket connects. A non-finite or `<= 0.0` price is
 * dropped (treated as *absent*, not `0.0`) so an unpriced coin keeps `usdValue = null` — shown but
 * excluded from the total — instead of being silently discarded downstream.
 */
fun spotTokenPrices(
    pairs: Map<Int, String>,
    mids: Map<String, Double>,
    seed: Map<String, Double> = emptyMap(),
): Map<Int, Double> =
    pairs.entries.mapNotNull { (tokenIdx, name) ->
        val px = mids[name] ?: seed[name]
        px?.takeIf { it.isFinite() && it > 0.0 }?.let { tokenIdx to it }
    }.toMap()
