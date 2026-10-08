package app.aaps.implementation.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the alarm volume curve.
 *
 * The numbers matter more than they look. This is the ramp on a glucose alarm: too slow and a
 * sleeping user is not woken, too fast and every alarm starts at full volume. The curve is
 * duplicated from the Android player while that class is still Android-only, so these assertions
 * are what would catch the two drifting apart.
 */
class AlarmVolumeRampTest {

    @Test
    fun `it starts silent`() {
        val ramp = AlarmVolumeRamp()
        ramp.start()

        assertEquals(0, ramp.level)
    }

    /** Logarithmic, so the first steps are quiet - an alarm should not begin at full blast. */
    @Test
    fun `the first step is quiet`() {
        val ramp = AlarmVolumeRamp()
        ramp.start()

        val first = ramp.next()

        assertTrue(first > 0f, "the first step should make some sound, was $first")
        assertTrue(first < 0.2f, "the first step should be quiet, was $first")
    }

    @Test
    fun `volume only ever rises`() {
        val ramp = AlarmVolumeRamp()
        ramp.start()
        var previous = 0f

        while (ramp.hasMore()) {
            val next = ramp.next()
            assertTrue(next >= previous, "volume fell from $previous to $next at level ${ramp.level}")
            previous = next
        }
    }

    /** The point of the ramp is that it arrives at full volume, not near it. */
    @Test
    fun `it reaches full volume by the last step`() {
        val ramp = AlarmVolumeRamp()
        ramp.start()
        var last = 0f
        while (ramp.hasMore()) last = ramp.next()

        assertFalse(ramp.hasMore())
        assertEquals(1f, last, absoluteTolerance = 0.001f)
    }

    /** Waits shrink as it climbs, so a missed alarm gets loud faster the longer it is ignored. */
    @Test
    fun `the wait between steps shrinks`() {
        val ramp = AlarmVolumeRamp()
        ramp.start()
        ramp.next()
        val early = ramp.delayMillis()
        repeat(5) { ramp.next() }
        val later = ramp.delayMillis()

        assertTrue(later < early, "delay did not shrink: $early then $later")
    }

    /** Floored, so the final steps do not turn into a stutter of volume changes. */
    @Test
    fun `the wait never drops below the floor`() {
        val ramp = AlarmVolumeRamp()
        ramp.start()

        while (ramp.hasMore()) {
            ramp.next()
            assertTrue(ramp.delayMillis() >= 2_000L, "delay ${ramp.delayMillis()} at level ${ramp.level}")
        }
    }
}
