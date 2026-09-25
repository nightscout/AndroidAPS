package app.aaps.core.data.datetime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [parseIsoToEpochMillisOrNull] on every target, including Kotlin/Native.
 *
 * `IsoDateParserParityTest` already checks every shape against real joda output, but joda is a JVM
 * library, so that test can never leave the JVM. This one states the same contract in terms the
 * parser itself can be held to anywhere.
 *
 * The zone-anchored shapes are asserted absolutely. The local-time shapes deliberately are not: they
 * resolve against the machine's own zone, so an absolute expectation here would pass or fail
 * depending on where the build runs. They are checked by the relationships that must hold instead.
 */
class IsoDateParserTest {

    @Test
    fun `the epoch itself parses to zero`() {
        assertEquals(0L, parseIsoToEpochMillisOrNull("1970-01-01T00:00:00.000Z"))
    }

    @Test
    fun `milliseconds are kept`() {
        val withMillis = parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.555Z")!!
        val withFewer = parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.055Z")!!
        val whole = parseIsoToEpochMillisOrNull("2026-08-06T04:56:19Z")!!
        assertEquals(500L, withMillis - withFewer)
        assertEquals(555L, withMillis - whole)
    }

    @Test
    fun `an offset shifts the instant by exactly that offset`() {
        val utc = parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.555Z")!!
        // A wall clock two hours ahead of UTC names an instant two hours earlier.
        assertEquals(utc - 2 * 3_600_000L, parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.555+02:00"))
        assertEquals(utc + 4 * 3_600_000L, parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.555-04:00"))
    }

    @Test
    fun `an offset without a colon means the same as one with`() {
        assertEquals(
            parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.555+02:00"),
            parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.555+0200")
        )
    }

    @Test
    fun `lower case t and z are accepted`() {
        assertEquals(
            parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.555Z"),
            parseIsoToEpochMillisOrNull("2026-08-06t04:56:19.555z")
        )
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(
            parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.555Z"),
            parseIsoToEpochMillisOrNull("  2026-08-06T04:56:19.555Z  ")
        )
    }

    @Test
    fun `a date alone is the same instant as that date at midnight`() {
        // Both are local, so only their equality is asserted - not what they equal.
        assertEquals(
            parseIsoToEpochMillisOrNull("2026-08-06T00:00"),
            parseIsoToEpochMillisOrNull("2026-08-06")
        )
    }

    @Test
    fun `a local time without a zone advances the same way a zoned one does`() {
        val early = parseIsoToEpochMillisOrNull("2026-08-06T04:56")!!
        val later = parseIsoToEpochMillisOrNull("2026-08-06T05:56")!!
        assertEquals(3_600_000L, later - early)
    }

    @Test
    fun `text that is not a timestamp gives null rather than 1970`() {
        // Null and not a sentinel on purpose: one caller maps it to 0 and another throws, and a silent
        // 1970 inside an APS result would be worse than a loud failure.
        assertNull(parseIsoToEpochMillisOrNull(""))
        assertNull(parseIsoToEpochMillisOrNull("   "))
        assertNull(parseIsoToEpochMillisOrNull("not a date"))
        assertNull(parseIsoToEpochMillisOrNull("2026-13-45T99:99:99Z"))
    }
}
