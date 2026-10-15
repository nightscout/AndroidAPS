package app.aaps.workflow

import app.aaps.core.data.time.T
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which phase may change the right edge of the graph window.
 *
 * One function, and the failure it prevents is silent: a series that is merely two hours short still
 * draws, it just stops before the axis does. The rule it carries is in [keptEndTime] - the prepare
 * phase widens but never narrows, because the phase that knows the horizon runs after it.
 *
 * In commonTest, so this runs on iOS too. Nothing here needs a platform.
 */
class GraphWindowTest {

    private val toTime = 1_000_000_000L
    private fun hours(h: Long) = T.hours(h).msecs()

    /** The first pass of all, and every pass of a history window: no horizon to keep. */
    @Test
    fun `an empty cache gives the window end itself`() {
        assertEquals(toTime, keptEndTime(null, toTime))
    }

    /**
     * The regression this function exists for. The pass that builds the IOB, activity and BGI series
     * runs before the one that knows the horizon, so writing `toTime` here cut them at the next full
     * hour while the axis went on to the newest prediction.
     */
    @Test
    fun `a known horizon is kept, not collapsed to the window end`() {
        val horizon = toTime + hours(4)

        assertEquals(horizon, keptEndTime(horizon, toTime))
    }

    /** The window moves on at every full hour, and the end moves with it rather than lagging. */
    @Test
    fun `a horizon behind the window end does not pull the end back`() {
        assertEquals(toTime, keptEndTime(toTime - hours(1), toTime))
    }

    @Test
    fun `a horizon exactly at the window end changes nothing`() {
        assertEquals(toTime, keptEndTime(toTime, toTime))
    }

    /**
     * A clock or time zone change can move `toTime` back by hours and leave a far future value in the
     * cache. That is not a horizon - no prediction runs that far - and walking to it would cost hours
     * of five minute steps for an edge nothing draws.
     */
    @Test
    fun `a value too far ahead to be a horizon is cut back`() {
        assertEquals(toTime + hours(6), keptEndTime(toTime + hours(30), toTime))
    }

    /** Zero is what "no loop run yet" looks like once it has been through a Long rather than a null. */
    @Test
    fun `a zero horizon does not drag the end back to 1970`() {
        assertEquals(toTime, keptEndTime(0L, toTime))
    }
}
