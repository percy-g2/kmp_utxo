package ktx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Parity tests for [groupThousands], the single-pass thousands-grouping helper that replaced the
 * `toString().reversed().chunked(3).joinToString(",").reversed()` idiom on the price-formatting hot
 * path. The new version must produce byte-identical output to the old one for every non-negative Long.
 */
class DoubleKtxTest {

    /** The exact grouping idiom this function replaced — the source of truth for parity. */
    private fun reference(value: Long): String =
        value.toString().reversed().chunked(3).joinToString(",").reversed()

    @Test
    fun groupThousands_matchesReferenceAtBoundaries() {
        val cases = listOf(
            0L, 1L, 12L, 123L, 999L, 1_000L, 1_001L, 1_234L, 9_999L,
            10_000L, 12_345L, 100_000L, 123_456L, 999_999L,
            1_000_000L, 1_234_567L, 12_345_678L, 123_456_789L,
            1_000_000_000L, 9_876_543_210L, Long.MAX_VALUE
        )
        for (v in cases) {
            assertEquals(reference(v), groupThousands(v), "grouping mismatch for $v")
        }
    }

    @Test
    fun groupThousands_matchesReferenceAcrossRanges() {
        // Sweep contiguous windows that straddle the 3-/6-/7-digit grouping boundaries.
        val windows = listOf(0L..2_100L, 998_000L..1_002_000L)
        for (window in windows) {
            for (v in window) {
                assertEquals(reference(v), groupThousands(v))
            }
        }
    }

    @Test
    fun groupThousands_neverEmitsLeadingSeparator() {
        // Regression guard: a digit count that is an exact multiple of 3 must not start with ','.
        for (v in listOf(100L, 123L, 100_000L, 123_456L, 100_000_000L)) {
            assertFalse(groupThousands(v).startsWith(","), "unexpected leading separator for $v")
        }
    }
}
