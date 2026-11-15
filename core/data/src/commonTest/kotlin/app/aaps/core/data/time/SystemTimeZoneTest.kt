package app.aaps.core.data.time

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Invariants that must hold for [systemUtcOffsetAt] on every platform.
 *
 * This value is not cosmetic. It is stored as `utcOffset` on every record, it takes part in
 * `contentEqualsTo` - so a changed value makes a record compare unequal and sync again - it is
 * uploaded to Nightscout, which validates it and answers 400 when it is wrong, and it goes to Open
 * Humans. A platform returning it in the wrong unit would corrupt data quietly rather than crash.
 *
 * The offset cannot be pinned to a literal, because it depends on the machine's time zone. What can
 * be pinned is its shape, and that is enough to catch the mistake this actually invites: returning
 * **seconds instead of milliseconds**. `NSTimeZone.secondsFromGMTForDate` is named for what it
 * returns, so the `* 1000` in the Apple actual is easy to drop.
 *
 * Honest limits: on a machine set to UTC every assertion here passes trivially, because the offset
 * is zero in any unit. CI often runs in UTC. These tests are therefore strongest on a developer
 * machine in a real zone, and they are still worth having - a units bug that survives to a Mac would
 * be caught the moment anyone runs them somewhere other than UTC.
 */
class SystemTimeZoneTest {

    // 2026-01-15T12:00:00Z and 2026-07-15T12:00:00Z - one in each half of the year, so that in a
    // zone with daylight saving exactly one of them is in DST.
    private val winter = 1_768_478_400_000L
    private val summer = 1_784_116_800_000L

    @Test
    fun `offset is a whole number of minutes`() {
        // Every zone in use today is a whole number of minutes from UTC. Seconds returned in place
        // of milliseconds would leave a remainder here for any non-zero offset.
        assertEquals(0L, systemUtcOffsetAt(winter) % 60_000L, "offset ${systemUtcOffsetAt(winter)} is not a whole number of minutes")
        assertEquals(0L, systemUtcOffsetAt(summer) % 60_000L, "offset ${systemUtcOffsetAt(summer)} is not a whole number of minutes")
    }

    @Test
    fun `offset is within the range of real time zones`() {
        // -12:00 (Baker Island) to +14:00 (Kiribati), in milliseconds.
        listOf(winter, summer).forEach { at ->
            val offset = systemUtcOffsetAt(at)
            assertTrue(offset in -12 * 3_600_000L..14 * 3_600_000L, "offset $offset at $at is outside any real time zone")
        }
    }

    @Test
    // Kotlin/Native rejects a comma in a backtick name, so this reads a little stiffly.
    fun `daylight saving changes the offset by a whole hour or not at all`() {
        // The strongest unit check available without pinning a zone: where DST applies the two
        // halves of the year differ by exactly one hour in milliseconds. An implementation
        // returning seconds would differ by 3600 instead, and fail here.
        val delta = abs(systemUtcOffsetAt(summer) - systemUtcOffsetAt(winter))
        assertTrue(
            delta == 0L || delta == 3_600_000L || delta == 1_800_000L,
            "summer/winter offsets differ by $delta ms, which is neither zero, half an hour nor a whole hour"
        )
    }

    @Test
    fun `the same moment always gives the same offset`() {
        // It is read per record and must not drift between calls, or contentEqualsTo would start
        // reporting records as changed when nothing changed.
        assertEquals(systemUtcOffsetAt(winter), systemUtcOffsetAt(winter))
        assertEquals(systemUtcOffsetAt(summer), systemUtcOffsetAt(summer))
    }
}
