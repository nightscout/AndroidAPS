package app.aaps.implementation.receivers

import app.aaps.core.data.pump.defs.TimeChangeType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The decision [classifyTimeChange] makes, which is the whole of what `IosTimeDateOrTZChangeWatcher`
 * has to get right.
 *
 * The notification centre is not driven here: iOS posts the two notifications itself and a test
 * cannot move the system zone, so what is left to prove is that the pair of values before and after
 * is read correctly. Getting it wrong is not cosmetic - a pump driver told "the zone changed" when
 * daylight saving started would set the pump clock an hour off, and a real zone change reported as a
 * plain clock change would leave the IOB of the last hours calculated in the old zone, because
 * `EventTimeZoneChanged` is sent for [TimeChangeType.TimezoneChanged] alone.
 *
 * The offsets are in milliseconds, as `systemUtcOffsetAt` returns them.
 */
class IosTimeDateOrTZChangeWatcherTest {

    private val prague = "Europe/Prague"
    private val london = "Europe/London"
    private val winterOffset = 3_600_000L   // +01:00
    private val summerOffset = 7_200_000L   // +02:00

    @Test
    fun `another zone is a zone change`() {
        assertEquals(
            TimeChangeType.TimezoneChanged,
            classifyTimeChange(prague, summerOffset, london, winterOffset)
        )
    }

    @Test
    fun `another zone with the same offset is still a zone change`() {
        // Prague and Stockholm are both +02:00 in summer. The hours shown are the same, but the pump
        // driver and the IOB calculation are told about the zone, not about the offset.
        assertEquals(
            TimeChangeType.TimezoneChanged,
            classifyTimeChange(prague, summerOffset, "Europe/Stockholm", summerOffset)
        )
    }

    @Test
    fun `a larger offset in the same zone is DST starting`() {
        assertEquals(
            TimeChangeType.DSTStarted,
            classifyTimeChange(prague, winterOffset, prague, summerOffset)
        )
    }

    @Test
    fun `a smaller offset in the same zone is DST ending`() {
        assertEquals(
            TimeChangeType.DSTEnded,
            classifyTimeChange(prague, summerOffset, prague, winterOffset)
        )
    }

    @Test
    fun `the same zone and the same offset is the clock being set`() {
        assertEquals(
            TimeChangeType.TimeChanged,
            classifyTimeChange(prague, summerOffset, prague, summerOffset)
        )
    }

    @Test
    fun `a zone with no daylight saving at all only ever reports the clock being set`() {
        // Asia/Kolkata stays at +05:30 the whole year, so nothing but a manual change can arrive.
        val kolkata = "Asia/Kolkata"
        val offset = 19_800_000L
        assertEquals(
            TimeChangeType.TimeChanged,
            classifyTimeChange(kolkata, offset, kolkata, offset)
        )
    }
}
