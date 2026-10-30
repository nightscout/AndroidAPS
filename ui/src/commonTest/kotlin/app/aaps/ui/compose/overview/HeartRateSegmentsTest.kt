package app.aaps.ui.compose.overview

import app.aaps.core.data.model.HR
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The heart rate line is drawn per stretch. It must break where the device sent nothing, and must
 * not break for a single missed minute.
 */
class HeartRateSegmentsTest {

    private fun min(m: Long) = m * 60_000L

    /** A reading measured from [startMin] to [endMin]. HR.timestamp is the end. */
    private fun hr(startMin: Long, endMin: Long, bpm: Double) =
        HR(duration = min(endMin - startMin), timestamp = min(endMin), beatsPerMinute = bpm, device = "watch", utcOffset = 0)

    private fun point(m: Long, bpm: Double) = GraphDataPoint(min(m), bpm)

    @Test
    fun readingsOneAfterAnotherMakeOneStretchThatEndsWithTheLastReading() {
        val result = heartRateSegments(listOf(hr(0, 1, 70.0), hr(1, 2, 72.0), hr(2, 3, 75.0)))
        assertEquals(listOf(listOf(point(0, 70.0), point(1, 72.0), point(2, 75.0), point(3, 75.0))), result)
    }

    @Test
    fun aGapLongerThanFiveMinutesStartsANewStretch() {
        val result = heartRateSegments(listOf(hr(0, 1, 70.0), hr(1, 2, 72.0), hr(30, 31, 90.0), hr(31, 32, 95.0)))
        assertEquals(
            listOf(
                listOf(point(0, 70.0), point(1, 72.0), point(2, 72.0)),
                listOf(point(30, 90.0), point(31, 95.0), point(32, 95.0))
            ),
            result
        )
    }

    @Test
    fun aMissedMinuteDoesNotBreakTheLine() {
        // The AAPS watch app sends nothing for a minute it could not measure
        val result = heartRateSegments(listOf(hr(0, 1, 70.0), hr(2, 3, 72.0)))
        assertEquals(listOf(listOf(point(0, 70.0), point(2, 72.0), point(3, 72.0))), result)
    }

    @Test
    fun exactlyFiveMinutesWithoutReadingsIsStillOneStretch() {
        val result = heartRateSegments(listOf(hr(0, 1, 70.0), hr(6, 7, 72.0)))
        assertEquals(1, result.size)
    }

    @Test
    fun longReadingsOneAfterAnotherAreNotAGap() {
        // A source that sends every 5 minutes: each reading starts where the one before it ended
        val result = heartRateSegments(listOf(hr(0, 5, 70.0), hr(5, 10, 72.0), hr(10, 15, 74.0)))
        assertEquals(listOf(listOf(point(0, 70.0), point(5, 72.0), point(10, 74.0), point(15, 74.0))), result)
    }

    @Test
    fun aLoneReadingIsAShortFlatLine() {
        val result = heartRateSegments(listOf(hr(10, 11, 80.0)))
        assertEquals(listOf(listOf(point(10, 80.0), point(11, 80.0))), result)
    }

    @Test
    fun readingsOutOfOrderAreSortedFirst() {
        val result = heartRateSegments(listOf(hr(1, 2, 72.0), hr(0, 1, 70.0)))
        assertEquals(listOf(listOf(point(0, 70.0), point(1, 72.0), point(2, 72.0))), result)
    }

    @Test
    fun noReadingsNoStretches() {
        assertEquals(emptyList(), heartRateSegments(emptyList()))
    }
}
