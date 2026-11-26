package app.aaps.ui.compose.overview.graphs

import kotlin.test.Test
import kotlin.test.assertEquals

/** The time labels under the graphs at different zoom levels, see [timeLabelStep] and [timeLabelValues]. */
class TimeLabelsTest {

    /** A label needs 150 px of room. */
    private val minSpacingPx = 150f

    @Test
    fun `zoomed out the labels stay on whole hours`() {
        // 6 hours on about 900 px: 2.5 px per minute, an hour is 150 px
        assertEquals(60, timeLabelStep(pxPerMinute = 2.5f, minSpacingPx = minSpacingPx))
        // A whole day: even hours are too close, Vico then leaves labels out as before
        assertEquals(60, timeLabelStep(pxPerMinute = 0.6f, minSpacingPx = minSpacingPx))
    }

    @Test
    fun `zoomed in the labels step down to half and quarter hours`() {
        assertEquals(30, timeLabelStep(pxPerMinute = 5f, minSpacingPx = minSpacingPx))
        assertEquals(15, timeLabelStep(pxPerMinute = 10f, minSpacingPx = minSpacingPx))
        // Never finer than a quarter hour
        assertEquals(15, timeLabelStep(pxPerMinute = 100f, minSpacingPx = minSpacingPx))
    }

    @Test
    fun `half hour labels fall on whole and half hours`() {
        // The first whole hour is 20 minutes after the start of the axis
        assertEquals(
            listOf(50.0, 80.0, 110.0, 140.0),
            timeLabelValues(visibleStart = 60.0, visibleEnd = 120.0, minX = 0.0, maxX = 1440.0, hourOffset = 20, step = 30)
        )
    }

    @Test
    fun `labels stay inside the axis`() {
        assertEquals(
            listOf(5.0, 20.0, 35.0),
            timeLabelValues(visibleStart = 0.0, visibleEnd = 30.0, minX = 0.0, maxX = 40.0, hourOffset = 20, step = 15)
        )
    }
}
