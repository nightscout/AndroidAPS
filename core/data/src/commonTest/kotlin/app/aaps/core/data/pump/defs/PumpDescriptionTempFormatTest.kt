package app.aaps.core.data.pump.defs

import kotlin.test.Test
import kotlin.test.assertEquals

class PumpDescriptionTempFormatTest {

    private fun format(step: Double, rate: Double) = PumpDescription().apply { tempAbsoluteStep = step }.tempAbsoluteFormat.format(rate).replace(',', '.')

    /** Seen on a real pump: it ran 0.275 U/h, and every screen said 0.28 U/h. */
    @Test
    fun aStepOf0025ShowsTheRealRate() {
        assertEquals("0.275", format(0.025, 0.275))
        assertEquals("0.325", format(0.025, 0.325))
    }

    @Test
    fun aRateWithTwoDigitsStaysShort() {
        assertEquals("0.15", format(0.025, 0.15))
        assertEquals("1.00", format(0.025, 1.0))
    }

    @Test
    fun otherPumpsLookAsBefore() {
        assertEquals("0.25", format(0.05, 0.25))
        assertEquals("0.27", format(0.01, 0.27))
        assertEquals("0.25", format(0.0, 0.25))
    }
}
