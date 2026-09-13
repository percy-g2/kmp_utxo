package ui.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyRangeTest {
    @Test
    fun positionsPriceWithinDailyRange() {
        assertEquals(0.25f, dailyRangePosition("125", "100", "200"))
        assertEquals(0.5f, dailyRangePosition("0.000002", "0.000001", "0.000003"))
    }

    @Test
    fun clampsTicksOutsideTheLastReportedRange() {
        assertEquals(0f, dailyRangePosition("90", "100", "200"))
        assertEquals(1f, dailyRangePosition("210", "100", "200"))
    }

    @Test
    fun hidesUnknownOrInvalidRanges() {
        assertNull(dailyRangePosition("bad", "100", "200"))
        assertNull(dailyRangePosition("100", "100", "100"))
        assertNull(dailyRangePosition("100", "200", "100"))
        assertNull(dailyRangePosition("NaN", "100", "200"))
        assertNull(dailyRangePosition("100", "0", "Infinity"))
    }
}
