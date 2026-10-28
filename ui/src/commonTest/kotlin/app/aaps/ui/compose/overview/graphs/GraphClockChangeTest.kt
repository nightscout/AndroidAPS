package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class GraphClockChangeTest {
    private val dst = Instant.parse("2020-10-25T01:00:00Z").toEpochMilliseconds()
    private val minute = 60_000L
    private fun reading(time: Long, minutes: Int?) =
        BgDataPoint(time, 100.0, BgRange.IN_RANGE, BgType.REGULAR, utcOffset = minutes?.times(minute))
    private fun changes(points: List<BgDataPoint>, zone: String = "UTC") =
        graphClockChanges(dst - minute, dst + 10 * minute, TimeZone.of(zone), points)

    @Test
    fun oldHistoryAndNewestFirstReadingsShowTravelAtTheFirstNewOffset() {
        val points = listOf(reading(dst - minute, 60), reading(dst + minute, -240))
        val change = changes(points.reversed() + points.last()).single()
        assertEquals(dst + minute, change.timestamp)
        assertEquals(-300, change.minutes)
    }

    @Test
    fun readingDetectedDstUsesTheExactTransitionRatherThanTwoNearbyLines() {
        val result = changes(listOf(reading(dst - minute, 60), reading(dst + 4 * minute, 0)), "Europe/London")
        assertEquals(dst, result.single().timestamp)
        assertEquals(-60, result.single().minutes)
    }

    @Test
    fun travelAndAnIndependentDstTransitionAreBothKept() {
        val result = changes(listOf(reading(dst + minute, 0), reading(dst + 5 * minute, 330)), "Europe/London")
        assertEquals(listOf(dst, dst + 5 * minute), result.map { it.timestamp })
        assertEquals(listOf(-60, 330), result.map { it.minutes })
    }

    @Test
    fun equalOrMissingOffsetsDoNotCreateSwitches() {
        assertEquals(emptyList(), changes(listOf(reading(dst, 60), reading(dst + minute, 60))))
        assertEquals(emptyList(), changes(listOf(reading(dst, null), reading(dst + minute, 60))))
        assertEquals(emptyList(), changes(listOf(reading(dst, 60), reading(dst + minute, null))))
    }

    @Test
    fun switchesOutsideTheWindowAreIgnoredAndTheEndIsIncluded() {
        val points = listOf(reading(dst - 2 * minute, 60), reading(dst - minute, 0), reading(dst + 10 * minute, 330), reading(dst + 11 * minute, 0))
        assertEquals(listOf(dst - minute, dst + 10 * minute), changes(points).map { it.timestamp })
        assertEquals(emptyList(), graphClockChanges(dst, dst, TimeZone.UTC, points))
    }
}
