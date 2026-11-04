package app.aaps.ui.compose.overview

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How far right the basal line, the target line and the running mode band are drawn.
 *
 * All three used to stop at `TimeRange.endTime`, which is not the right edge of the chart: the axis
 * is `max(newest BG / bucketed / prediction point, endTime)`, and `endTime` is the shorter of the
 * two whenever a prediction reaches past the two-hour clamp, or whenever the pass that restores the
 * horizon has not run. A series that stops there ends before the axis does, and on the basal graph
 * that looks like the line simply stopping short of "now".
 *
 * This is the edge for a reader who has the predictions overlay **on**. With it off the axis stops
 * at `TimeRange.toTime` and `OverviewDataCacheImpl.graphEndTime` returns that instead, without
 * reaching this function at all.
 *
 * Small enough to read at a glance, and worth pinning anyway: it is one `max` that three callers
 * share, and the failure it prevents is silent - a graph that is merely a bit short still draws.
 */
class GraphEndTimeTest {

    private val toTime = 1_000_000L

    /** No loop run on record: the range's own end is all there is to go on. */
    @Test
    fun `an unknown horizon leaves the range end alone`() {
        assertEquals(toTime, graphEndTime(null, toTime))
    }

    @Test
    fun `a prediction past the range end extends the graph to it`() {
        val prediction = toTime + 2 * 60 * 60 * 1000L

        assertEquals(prediction, graphEndTime(prediction, toTime))
    }

    /**
     * The range end wins when it is the later of the two, which is the ordinary case: `toTime` is
     * rounded up to the next full hour, so it usually sits ahead of a short prediction horizon.
     * Taking the prediction here would *shorten* the graph, turning a fix into the same bug.
     */
    @Test
    fun `a prediction inside the range does not shorten the graph`() {
        assertEquals(toTime, graphEndTime(toTime - 60 * 60 * 1000L, toTime))
    }

    /**
     * Zero is what "no APS result yet" looks like once it has been through a `Long` field rather
     * than a null, and it must not drag the right edge back to 1970.
     */
    @Test
    fun `a zero horizon does not drag the graph back`() {
        assertEquals(toTime, graphEndTime(0L, toTime))
    }

    @Test
    fun `a horizon exactly at the range end changes nothing`() {
        assertEquals(toTime, graphEndTime(toTime, toTime))
    }
}
