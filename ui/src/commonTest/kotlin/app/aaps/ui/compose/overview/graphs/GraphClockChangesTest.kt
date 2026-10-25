package app.aaps.ui.compose.overview.graphs

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class GraphClockChangesTest {

    private val london = TimeZone.of("Europe/London")
    private fun timestamp(value: String): Long = Instant.parse(value).toEpochMilliseconds()

    @Test
    fun clocksMoveBackAtTheExactInstant() {
        val changes = graphClockChanges(
            timestamp("2026-10-24T22:17:23Z"), timestamp("2026-10-25T04:17:23Z"), london
        )

        assertEquals(1, changes.size)
        val change = changes.single()
        assertEquals(timestamp("2026-10-25T01:00:00Z"), change.timestamp)
        assertEquals(-60, change.minutes)
        assertEquals(3600, change.before.totalSeconds)
        assertEquals(0, change.after.totalSeconds)
    }

    @Test
    fun clocksMoveForwardAtTheExactInstant() {
        val changes = graphClockChanges(
            timestamp("2026-03-29T00:17:23Z"), timestamp("2026-03-29T05:17:23Z"), london
        )

        assertEquals(1, changes.size)
        val change = changes.single()
        assertEquals(timestamp("2026-03-29T01:00:00Z"), change.timestamp)
        assertEquals(60, change.minutes)
        assertEquals(0, change.before.totalSeconds)
        assertEquals(3600, change.after.totalSeconds)
    }

    @Test
    fun repeatedHoursKeepSimpleLabelsWithoutOffsets() {
        assertEquals("01", graphTimeLabel(timestamp("2026-10-25T00:00:00Z"), london))
        assertEquals("01", graphTimeLabel(timestamp("2026-10-25T01:00:00Z"), london))
        assertEquals("00", graphTimeLabel(timestamp("2026-10-24T23:00:00Z"), london))
        assertEquals("02", graphTimeLabel(timestamp("2026-10-25T02:00:00Z"), london))
    }

    @Test
    fun forwardChangeSkipsAnHourWithoutAmbiguousLabels() {
        assertEquals("00", graphTimeLabel(timestamp("2026-03-29T00:00:00Z"), london))
        assertEquals("02", graphTimeLabel(timestamp("2026-03-29T01:00:00Z"), london))
        assertEquals("03", graphTimeLabel(timestamp("2026-03-29T02:00:00Z"), london))
    }

    @Test
    fun negativeOffsetsAlsoKeepSimpleLabels() {
        val newYork = TimeZone.of("America/New_York")
        assertEquals("01", graphTimeLabel(timestamp("2026-11-01T05:00:00Z"), newYork))
        assertEquals("01", graphTimeLabel(timestamp("2026-11-01T06:00:00Z"), newYork))
    }

    @Test
    fun halfHourChangesAreNotAssumedToBeOneHour() {
        val lordHowe = TimeZone.of("Australia/Lord_Howe")
        val backward = graphClockChanges(
            timestamp("2026-04-04T14:00:00Z"), timestamp("2026-04-04T17:00:00Z"), lordHowe
        ).single()
        val forward = graphClockChanges(
            timestamp("2026-10-03T14:00:00Z"), timestamp("2026-10-03T17:00:00Z"), lordHowe
        ).single()

        assertEquals(timestamp("2026-04-04T15:00:00Z"), backward.timestamp)
        assertEquals(-30, backward.minutes)
        assertEquals(timestamp("2026-10-03T15:30:00Z"), forward.timestamp)
        assertEquals(30, forward.minutes)
        assertEquals("01:30", graphTimeLabel(timestamp("2026-04-04T14:30:00Z"), lordHowe))
        assertEquals("01:30", graphTimeLabel(timestamp("2026-04-04T15:00:00Z"), lordHowe))
        assertEquals("02:30", graphTimeLabel(timestamp("2026-10-03T15:30:00Z"), lordHowe))
    }

    @Test
    fun unchangedOffsetsAndNormalMidnightsHaveNoMarker() {
        val start = timestamp("2026-10-24T22:00:00Z")
        val end = timestamp("2026-10-25T06:00:00Z")
        assertTrue(graphClockChanges(start, end, TimeZone.UTC).isEmpty())
        assertTrue(graphClockChanges(start, end, TimeZone.of("Asia/Kolkata")).isEmpty())
        assertEquals("05:30", graphTimeLabel(timestamp("2026-10-25T00:00:00Z"), TimeZone.of("Asia/Kolkata")))
    }

    @Test
    fun changesOnRangeEdgesAreIncludedButNotOutsideTheRange() {
        val change = timestamp("2026-10-25T01:00:00Z")
        assertEquals(change, graphClockChanges(change, change + 1, london).single().timestamp)
        assertEquals(change, graphClockChanges(change - 1, change, london).single().timestamp)
        assertTrue(graphClockChanges(change + 1, change + 3_600_000, london).isEmpty())
        assertTrue(graphClockChanges(change - 3_600_000, change - 1, london).isEmpty())
        assertTrue(graphClockChanges(change, change, london).isEmpty())
    }

    @Test
    fun bothChangesAreFoundInALongerRange() {
        val changes = graphClockChanges(
            timestamp("2026-01-01T00:00:00Z"), timestamp("2026-12-31T23:00:00Z"), london
        )
        assertEquals(listOf(60, -60), changes.map { it.minutes })
    }

    @Test
    fun elapsedTimeCoordinatesKeepBothHoursAndHaveNoSpringGap() {
        val autumn = timestamp("2026-10-25T00:00:00Z")
        val spring = timestamp("2026-03-29T00:00:00Z")
        assertEquals(60.0, timestampToX(autumn + 3_600_000, autumn))
        assertEquals(60.0, timestampToX(spring + 3_600_000, spring))
    }
}
