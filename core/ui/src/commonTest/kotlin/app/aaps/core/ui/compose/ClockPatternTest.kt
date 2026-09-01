package app.aaps.core.ui.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which clock a time pattern asks for.
 *
 * The case that matters is Traditional Chinese. Its short time pattern is `Bh:mm`, where `B` is the
 * flexible day period and `h` is a 12 hour hour. Reading the AM/PM letter finds no `a` there and
 * answers "24 hour", which is the wrong picker for that user. Every test below exists to keep that
 * from coming back.
 *
 * In `commonTest` rather than `iosTest` because the reader moved to `commonMain` - iOS and the
 * desktop target both use it now, and a test that only ran on one of them would stop covering the
 * code it was written for. The two checks that drive a real `NSDateFormatter` cannot be shared and
 * stay in `ClockPatternIosLocaleTest`.
 */
class ClockPatternTest {

    @Test
    fun `the plain twelve hour pattern reads as twelve hour`() {
        assertEquals(true, usesTwelveHourClock("h:mm a"))
    }

    @Test
    fun `the plain twenty four hour pattern reads as twenty four hour`() {
        assertEquals(false, usesTwelveHourClock("HH:mm"))
    }

    /** The regression. No `a` in it anywhere, and still a 12 hour clock. */
    @Test
    fun `a day period written as B is still a twelve hour clock`() {
        assertEquals(true, usesTwelveHourClock("Bh:mm"))
    }

    @Test
    fun `both spellings of each hour field are understood`() {
        assertEquals(true, usesTwelveHourClock("K:mm a"))
        assertEquals(false, usesTwelveHourClock("k:mm"))
    }

    @Test
    fun `an hour letter inside a quoted literal is text and not a field`() {
        assertEquals(false, usesTwelveHourClock("HH'h'mm"))
        assertEquals(true, usesTwelveHourClock("h 'o''clock'"))
    }

    @Test
    fun `a pattern naming no hour has no answer to give`() {
        assertNull(usesTwelveHourClock("mm:ss"))
        assertNull(usesTwelveHourClock(""))
    }
}
