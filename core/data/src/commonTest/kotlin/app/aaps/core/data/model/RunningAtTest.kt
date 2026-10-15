package app.aaps.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [latestRunningAt] must pick exactly what the "active at" database queries pick, and [endOf] must not
 * overflow for an entry without an end.
 */
class RunningAtTest {

    private fun tb(id: Long, start: Long, minutes: Long) =
        TB(id = id, timestamp = start, utcOffset = 0, type = TB.Type.NORMAL, isAbsolute = true, rate = 1.0, duration = minutes * MINUTE)

    private fun tt(id: Long, start: Long, duration: Long) =
        TT(id = id, timestamp = start, utcOffset = 0, reason = TT.Reason.ACTIVITY, highTarget = 140.0, lowTarget = 140.0, duration = duration)

    @Test
    fun startIsIncludedAndEndIsNot() {
        val list = listOf(tb(1, 10 * MINUTE, 30))
        assertNull(list.latestRunningAt(10 * MINUTE - 1) { it.duration })
        assertEquals(1, list.latestRunningAt(10 * MINUTE) { it.duration }?.id)
        assertEquals(1, list.latestRunningAt(40 * MINUTE - 1) { it.duration }?.id)
        assertNull(list.latestRunningAt(40 * MINUTE) { it.duration })
    }

    /** "ORDER BY timestamp DESC LIMIT 1": of two running entries, the one that started last. */
    @Test
    fun ofTwoRunningEntriesTheLaterStartedWins() {
        val list = listOf(tb(1, 0, 120), tb(2, 30 * MINUTE, 30))
        assertEquals(1, list.latestRunningAt(20 * MINUTE) { it.duration }?.id)
        assertEquals(2, list.latestRunningAt(40 * MINUTE) { it.duration }?.id)
        // the later one has ended, the long earlier one is still running
        assertEquals(1, list.latestRunningAt(70 * MINUTE) { it.duration }?.id)
    }

    @Test
    fun aGapAndAnEmptyListGiveNothing() {
        val list = listOf(tb(1, 0, 10), tb(2, 20 * MINUTE, 10))
        assertNull(list.latestRunningAt(15 * MINUTE) { it.duration })
        assertNull(emptyList<TB>().latestRunningAt(15 * MINUTE) { it.duration })
    }

    /**
     * A scene without a duration stores its temporary target with duration Long.MAX_VALUE. With
     * `timestamp + duration` the end wrapped to a negative number, the target looked finished, and the
     * overview drew the profile target instead (issue #5301). The database query finds it as running.
     */
    @Test
    fun anEntryWithoutAnEndIsRunning() {
        val list = listOf(tt(1, START, Long.MAX_VALUE))
        assertNull(list.latestRunningAt(START - 1) { it.duration })
        assertEquals(1, list.latestRunningAt(START) { it.duration }?.id)
        assertEquals(1, list.latestRunningAt(START + 365 * DAY) { it.duration }?.id)
    }

    @Test
    fun anEntryWithoutAnEndStartedLaterStillWins() {
        val list = listOf(tt(1, START - DAY, 2 * DAY), tt(2, START, Long.MAX_VALUE))
        assertEquals(1, list.latestRunningAt(START - 1) { it.duration }?.id)
        assertEquals(2, list.latestRunningAt(START) { it.duration }?.id)
    }

    @Test
    fun endOfAddsWhenTheSumFits() {
        assertEquals(40 * MINUTE, endOf(10 * MINUTE, 30 * MINUTE))
        assertEquals(START, endOf(START, 0))
        assertEquals(Long.MAX_VALUE, endOf(0, Long.MAX_VALUE))
    }

    @Test
    fun endOfStopsAtLongMaxValue() {
        assertEquals(Long.MAX_VALUE, endOf(START, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, endOf(START, Long.MAX_VALUE - START + 1))
        assertEquals(Long.MAX_VALUE - 1, endOf(START, Long.MAX_VALUE - START - 1))
        // The same duration after a trip through Nightscout in whole minutes is a little smaller, still no end.
        assertEquals(Long.MAX_VALUE, endOf(START, Long.MAX_VALUE / MINUTE * MINUTE))
    }

    @Test
    fun aTargetWithoutAnEndHasNoEnd() {
        val sceneTarget = tt(1, START, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, sceneTarget.end)
        assertFalse(sceneTarget.hasEnd)

        val normalTarget = tt(2, START, 30 * MINUTE)
        assertEquals(START + 30 * MINUTE, normalTarget.end)
        assertTrue(normalTarget.hasEnd)
    }

    @Test
    fun anEventWithoutAnEndHasNoEnd() {
        fun te(duration: Long) = TE(timestamp = START, utcOffset = 0, duration = duration, type = TE.Type.EXERCISE, glucoseUnit = GlucoseUnit.MGDL)
        assertFalse(te(Long.MAX_VALUE).hasEnd)
        assertTrue(te(30 * MINUTE).hasEnd)
        assertTrue(te(0).hasEnd)
    }

    private companion object {

        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE

        /** 2026-10-14, a real epoch time: the overflow needs one. */
        const val START = 1_791_990_000_000L
    }
}
