package app.aaps.shared.impl.utils

import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The part of [DateUtilImpl] that must answer the same on every platform.
 *
 * These run on Android and on the iOS simulator from one source. That is the point: the values
 * checked here are timestamps and wire format strings, so a platform difference would be a data
 * bug, not a cosmetic one. Anything whose answer depends on the device's language or clock setting
 * is deliberately absent - that is [DateFormatPlatform]'s job and it is faked here.
 */
class DateUtilImplCommonTest {

    /** Fixed so a test reads the same in every time zone the CI or a laptop happens to be in. */
    private val fixedNow = 1_513_902_750_000L // 2017-12-22T00:32:30Z

    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(fixedNow)
    }

    /** Records what it was asked for, so tests can assert on the pattern rather than the output. */
    private class RecordingPlatform(private val is24: Boolean = true) : DateFormatPlatform {

        val patterns = mutableListOf<String>()

        override fun is24Hour(): Boolean = is24
        override fun format(millis: Long, pattern: String): String {
            patterns += pattern
            return pattern
        }

        override fun localizedShortDate(millis: Long): String = "SHORT"
        override fun standardUtcOffsetMillis(): Long = 0L
    }

    private val platform = RecordingPlatform()
    private val sut = DateUtilImpl(platform, clock)

    private val text = object : TextResolver {
        override fun gs(ref: TextRef): String = name(ref)
        override fun gs(ref: TextRef, vararg args: Any?): String = name(ref) + args.joinToString(prefix = "(", postfix = ")")
        override fun gsNotLocalised(ref: TextRef): String = name(ref)
        override fun shortTextMode(): Boolean = false
        private fun name(ref: TextRef) = when (ref) {
            is TextRef.Named      -> ref.name
            is TextRef.Literal    -> ref.text
            is TextRef.AndroidRes -> "res"
        }
    }

    // ---- the wire format ------------------------------------------------------------------------

    @Test
    fun `toISOString is the format Nightscout is given`() {
        assertEquals("2017-12-22T00:32:30.000Z", sut.toISOString(fixedNow))
    }

    @Test
    fun `toISOString keeps milliseconds`() {
        assertEquals("2017-12-22T00:32:30.417Z", sut.toISOString(fixedNow + 417))
    }

    @Test
    fun `toISOAsUTC keeps the trailing zeros AAPS has always sent`() {
        assertEquals("2017-12-22T00:32:30.0000000Z", sut.toISOAsUTC(fixedNow))
    }

    @Test
    fun `an ISO string with a Z offset parses back to the same instant`() {
        assertEquals(1513902750000L, sut.fromISODateString("2017-12-22T00:32:30Z"))
    }

    @Test
    fun `an ISO string with milliseconds parses back to the same instant`() {
        assertEquals(1512317365000L, sut.fromISODateString("2017-12-03T16:09:25.000Z"))
    }

    @Test
    fun `an ISO string with a numeric offset parses back to the same instant`() {
        // `+0200` with no colon is what some Nightscout servers send.
        assertEquals(1511124634417L, sut.fromISODateString("2017-11-19T22:50:34.417+0200"))
        assertEquals(1511124634000L, sut.fromISODateString("2017-11-19T22:50:34+0200"))
    }

    @Test
    fun `every ISO value survives a round trip`() {
        val original = 1_600_000_123_456L
        assertEquals(original, sut.fromISODateString(sut.toISOString(original)))
    }

    // ---- patterns handed to the platform ---------------------------------------------------------

    @Test
    fun `a 24 hour device is asked for 24 hour patterns`() {
        val on24 = DateUtilImpl(RecordingPlatform(is24 = true).also { it.format(0, "warmup") }, clock)
        assertEquals("HH:mm", on24.timeString(fixedNow))
        assertEquals("HH:mm:ss", on24.timeStringWithSeconds(fixedNow))
        assertEquals("HH", on24.hourString(fixedNow))
        assertEquals("dd/MM", on24.dateStringShort(fixedNow))
    }

    @Test
    fun `a 12 hour device is asked for AM PM patterns`() {
        val on12 = DateUtilImpl(RecordingPlatform(is24 = false), clock)
        assertEquals("hh:mm a", on12.timeString(fixedNow))
        assertEquals("hh:mm:ss a", on12.timeStringWithSeconds(fixedNow))
        assertEquals("hh", on12.hourString(fixedNow))
        assertEquals("MM/dd", on12.dateStringShort(fixedNow))
    }

    @Test
    fun `a caller's own pattern reaches the platform unchanged`() {
        sut.dayNameString(fixedNow, "EEEE")
        sut.monthString(fixedNow, "MMM")

        // Both java.time and NSDateFormatter read LDML patterns, which is what lets call sites keep
        // passing their own.
        assertTrue(platform.patterns.containsAll(listOf("EEEE", "MMM")))
    }

    // ---- the clock -------------------------------------------------------------------------------

    @Test
    fun `now comes from the injected clock`() {
        assertEquals(fixedNow, sut.now())
    }

    @Test
    fun `nowWithoutMilliseconds drops only the milliseconds`() {
        val util = DateUtilImpl(platform, object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(fixedNow + 417)
        })
        assertEquals(fixedNow, util.nowWithoutMilliseconds())
    }

    @Test
    fun `isOlderThan measures against the injected clock`() {
        assertTrue(sut.isOlderThan(fixedNow - 11 * 60_000L, 10))
        assertTrue(!sut.isOlderThan(fixedNow - 9 * 60_000L, 10))
    }

    // ---- plain arithmetic -------------------------------------------------------------------------

    @Test
    fun `the same instant is the same day as itself`() {
        assertTrue(sut.isSameDay(fixedNow, fixedNow))
    }

    @Test
    fun `two instants a week apart are not the same day`() {
        assertTrue(!sut.isSameDay(fixedNow, fixedNow + 7 * 86_400_000L))
    }

    @Test
    fun `beginOfDay is at or before its instant and within a day of it`() {
        val start = sut.beginOfDay(fixedNow)
        assertTrue(start <= fixedNow)
        assertTrue(fixedNow - start < 86_400_000L)
    }

    @Test
    fun `beginOfDay is stable`() {
        assertEquals(sut.beginOfDay(fixedNow), sut.beginOfDay(sut.beginOfDay(fixedNow)))
    }

    @Test
    fun `formatHHMM pads both halves`() {
        assertEquals("01:01", sut.formatHHMM(3660))
        assertEquals("00:00", sut.formatHHMM(0))
        assertEquals("10:30", sut.formatHHMM(37800))
    }

    @Test
    fun `toSeconds reads both clock styles`() {
        assertEquals(3600, sut.toSeconds("01:00"))
        assertEquals(3600, sut.toSeconds("01:00 a.m."))
        assertEquals(3600, sut.toSeconds("01:00 AM"))
        assertEquals(46800, sut.toSeconds("01:00 PM"))
        assertEquals(0, sut.toSeconds("12:00 AM"))
    }

    @Test
    fun `computeDiff splits a duration into its parts`() {
        val diff = sut.computeDiff(0L, 100_000L)

        assertEquals(0L, diff.days)
        assertEquals(0L, diff.hours)
        assertEquals(1L, diff.minutes)
        assertEquals(40L, diff.seconds)
    }

    @Test
    fun `qs prints only the digits it needs`() {
        assertEquals("12", sut.qs(12.0, 0))
        assertEquals("12.3", sut.qs(12.34, 1))
        // The separator is always a dot here, whatever the device's language is, because these
        // values are stored rather than shown.
        assertTrue(!sut.qs(1234.5, 1).contains(","))
    }

    @Test
    fun `timeZoneByOffset names UTC for a zero offset`() {
        assertEquals("UTC", sut.timeZoneByOffset(0L))
    }

    @Test
    fun `an empty timestamp formats as an empty string`() {
        assertEquals("", sut.dateAndTimeString(0L))
        assertEquals(null, sut.dateAndTimeStringNullable(0L))
        assertEquals("", sut.dateAndTimeAndSecondsString(0L))
    }

    @Test
    fun `a null time gives an empty string rather than throwing`() {
        assertEquals("", sut.minAgo(text, null))
        assertEquals("", sut.minAgoShort(null))
        assertEquals("", sut.minAgoLong(text, null))
    }
}
