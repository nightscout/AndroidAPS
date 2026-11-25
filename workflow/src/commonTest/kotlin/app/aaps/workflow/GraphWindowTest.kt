package app.aaps.workflow

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.liveGraphWindowEnd
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Which phase may change the right edge of the graph window, and how a live run moves the window on.
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
    fun `a known horizon is kept rather than collapsed to the window end`() {
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

    // ---- moving the live window (#5614) ------------------------------------------------------------

    private class Window(override var toTime: Long, override var fromTime: Long, override var endTime: Long) : OverviewData

    /** 2026-11-18 11:25:07 UTC, the first reading after the gap in the issue. */
    private val now = Instant.parse("2026-11-18T11:25:07Z")
    private val endOfNowsHour = Instant.parse("2026-11-18T12:00:00Z").toEpochMilliseconds() + 100_000

    @Test
    fun `the live window ends at the next full hour plus 100 s`() {
        assertEquals(endOfNowsHour, liveGraphWindowEnd(now, TimeZone.UTC))
    }

    /** Local hours: +05:30 does not line up with the UTC hour. */
    @Test
    fun `the live window ends at the next full local hour`() {
        val zone = TimeZone.of("Asia/Kolkata") // 11:25 UTC is 16:55 there
        assertEquals(Instant.parse("2026-11-18T11:30:00Z").toEpochMilliseconds() + 100_000, liveGraphWindowEnd(now, zone))
    }

    /**
     * The window of the run before the gap ended at 10:01:40. Not moved, the readings after it were not picked,
     * the newest one too.
     */
    @Test
    fun `a window left behind by the clock is moved on and keeps its width`() {
        val oldToTime = Instant.parse("2026-11-18T10:01:40Z").toEpochMilliseconds()
        val window = Window(toTime = oldToTime, fromTime = oldToTime - hours(22), endTime = oldToTime + hours(2))

        window.moveLiveWindowTo(now, TimeZone.UTC)

        assertEquals(endOfNowsHour, window.toTime)
        assertEquals(endOfNowsHour - hours(22), window.fromTime)
        // The old horizon is behind the new end: widened to it, not kept behind it
        assertEquals(endOfNowsHour, window.endTime)
    }

    @Test
    fun `a horizon past the new window end is kept`() {
        val oldToTime = endOfNowsHour - hours(1)
        val horizon = endOfNowsHour + hours(2)
        val window = Window(toTime = oldToTime, fromTime = oldToTime - hours(24), endTime = horizon)

        window.moveLiveWindowTo(now, TimeZone.UTC)

        assertEquals(endOfNowsHour, window.toTime)
        assertEquals(horizon, window.endTime)
    }

    /** The window of this hour, as the run before left it: nothing to do. */
    @Test
    fun `a window that is up to date is not changed`() {
        val window = Window(toTime = endOfNowsHour, fromTime = endOfNowsHour - hours(23), endTime = endOfNowsHour + hours(3))

        window.moveLiveWindowTo(now, TimeZone.UTC)

        assertEquals(endOfNowsHour, window.toTime)
        assertEquals(endOfNowsHour - hours(23), window.fromTime)
        assertEquals(endOfNowsHour + hours(3), window.endTime)
    }

    /** After a clock or time zone change back: moving the window back is left to the post pass, as before. */
    @Test
    fun `a window ahead of the clock is not moved back`() {
        val ahead = endOfNowsHour + hours(5)
        val window = Window(toTime = ahead, fromTime = ahead - hours(24), endTime = ahead)

        window.moveLiveWindowTo(now, TimeZone.UTC)

        assertEquals(ahead, window.toTime)
        assertEquals(ahead - hours(24), window.fromTime)
    }
}
