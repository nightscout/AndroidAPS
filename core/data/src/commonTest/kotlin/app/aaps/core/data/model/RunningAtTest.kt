package app.aaps.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [latestRunningAt] must pick exactly what the "active at" database queries pick. */
class RunningAtTest {

    private fun tb(id: Long, start: Long, minutes: Long) =
        TB(id = id, timestamp = start, utcOffset = 0, type = TB.Type.NORMAL, isAbsolute = true, rate = 1.0, duration = minutes * MINUTE)

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

    private companion object {

        const val MINUTE = 60_000L
    }
}
