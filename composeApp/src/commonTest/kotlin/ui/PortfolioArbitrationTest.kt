package ui

import model.HlAssetPosition
import model.HlMarginSummary
import model.HlPerpPosition
import model.HlPerpState
import model.HlSpotBalance
import model.HlSpotState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the frame-arbitration helpers that fix the "two values / blinking positions" bug:
 * webData2 (WS) is the source of truth, HTTP only bootstraps, older/partial frames are rejected.
 */
class PortfolioArbitrationTest {

    private val btcPosition = HlAssetPosition(
        type = "oneWay",
        position = HlPerpPosition(coin = "BTC", szi = "-0.04339"),
    )

    private fun perp(
        time: Long,
        accountValue: String = "0",
        marginUsed: String = "0",
        positions: List<HlAssetPosition> = emptyList(),
    ) = HlPerpState(
        marginSummary = HlMarginSummary(accountValue = accountValue, totalMarginUsed = marginUsed),
        withdrawable = "0",
        assetPositions = positions,
        time = time,
    )

    private fun spot(time: Long, balances: List<HlSpotBalance> = emptyList()) =
        HlSpotState(balances = balances, time = time)

    // --- isPartialPerp ---

    @Test
    fun partial_when_no_positions_but_margin_used() {
        assertTrue(isPartialPerp(perp(time = 1, marginUsed = "520.69")))
        assertTrue(isPartialPerp(perp(time = 1, accountValue = "529.27")))
    }

    @Test
    fun not_partial_when_truly_empty_account() {
        // A genuinely empty/flat account (no positions, no margin, no equity) is legitimate.
        assertFalse(isPartialPerp(perp(time = 1)))
    }

    @Test
    fun not_partial_when_positions_present() {
        assertFalse(isPartialPerp(perp(time = 1, accountValue = "529.27", positions = listOf(btcPosition))))
    }

    // --- shouldApplyPerp: source priority ---

    @Test
    fun first_perp_frame_is_always_applied() {
        assertTrue(shouldApplyPerp(current = null, currentFromWs = false, incoming = perp(1, positions = listOf(btcPosition)), incomingFromWs = true))
    }

    @Test
    fun ws_perp_supersedes_http_even_with_older_time() {
        val httpHeld = perp(time = 1000, accountValue = "538", positions = listOf(btcPosition))
        val wsFrame = perp(time = 1, accountValue = "529", positions = listOf(btcPosition))
        assertTrue(shouldApplyPerp(current = httpHeld, currentFromWs = false, incoming = wsFrame, incomingFromWs = true))
    }

    @Test
    fun http_perp_never_displaces_ws() {
        val wsHeld = perp(time = 1, accountValue = "529", positions = listOf(btcPosition))
        val httpFrame = perp(time = 1000, accountValue = "538", positions = listOf(btcPosition))
        assertFalse(shouldApplyPerp(current = wsHeld, currentFromWs = true, incoming = httpFrame, incomingFromWs = false))
    }

    // --- shouldApplyPerp: monotonic time within a source ---

    @Test
    fun older_same_source_perp_is_rejected() {
        val held = perp(time = 100, positions = listOf(btcPosition))
        val older = perp(time = 50, positions = listOf(btcPosition))
        assertFalse(shouldApplyPerp(current = held, currentFromWs = true, incoming = older, incomingFromWs = true))
    }

    @Test
    fun newer_same_source_perp_is_applied() {
        val held = perp(time = 50, positions = listOf(btcPosition))
        val newer = perp(time = 100, positions = listOf(btcPosition))
        assertTrue(shouldApplyPerp(current = held, currentFromWs = true, incoming = newer, incomingFromWs = true))
    }

    @Test
    fun unknown_time_zero_perp_is_applied() {
        // webData2 may omit `time` (=> 0). Don't let an unknown timestamp block a live frame.
        val held = perp(time = 100, positions = listOf(btcPosition))
        val unknown = perp(time = 0, positions = listOf(btcPosition))
        assertTrue(shouldApplyPerp(current = held, currentFromWs = true, incoming = unknown, incomingFromWs = true))
    }

    // --- shouldApplyPerp: partial guard wins ---

    @Test
    fun partial_perp_is_rejected_even_as_first_ws_frame() {
        assertFalse(shouldApplyPerp(current = null, currentFromWs = false, incoming = perp(1, accountValue = "529"), incomingFromWs = true))
    }

    // --- shouldApplySpot ---

    @Test
    fun first_spot_frame_is_always_applied() {
        assertTrue(shouldApplySpot(current = null, currentFromWs = false, incoming = spot(1), incomingFromWs = true))
    }

    @Test
    fun ws_spot_supersedes_http_and_http_never_displaces_ws() {
        val httpHeld = spot(time = 1000, balances = listOf(HlSpotBalance(coin = "USDC", total = "8.78")))
        val wsFrame = spot(time = 1)
        assertTrue(shouldApplySpot(current = httpHeld, currentFromWs = false, incoming = wsFrame, incomingFromWs = true))
        assertFalse(shouldApplySpot(current = wsFrame, currentFromWs = true, incoming = httpHeld, incomingFromWs = false))
    }

    @Test
    fun older_same_source_spot_is_rejected() {
        assertFalse(shouldApplySpot(current = spot(100), currentFromWs = true, incoming = spot(50), incomingFromWs = true))
    }
}
