package app.aaps.ui.compose.overview

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The basal graph looks its values up at the full minute `IobCobCalculator.getBasalData` used, which
 * rounds up with `AutosensDataStore.roundUpTime`. Both must round the same way, or a TBR that starts
 * in the middle of a minute would be drawn one minute off.
 */
class RoundUpToMinuteTest {

    @Test
    fun aFullMinuteStays() {
        assertEquals(120_000L, roundUpToMinute(120_000L))
    }

    @Test
    fun anythingAfterAFullMinuteGoesToTheNextOne() {
        assertEquals(180_000L, roundUpToMinute(120_001L))
        assertEquals(180_000L, roundUpToMinute(179_999L))
    }
}
