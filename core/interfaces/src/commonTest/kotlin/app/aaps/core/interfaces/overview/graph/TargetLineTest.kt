package app.aaps.core.interfaces.overview.graph

import app.aaps.core.data.model.TT
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The target line as a step line: the profile's target middle, the temporary target's middle while one runs,
 * only the changes kept, and a last point at the end of the range. Names without a comma: the iOS test runner
 * cannot take one.
 */
class TargetLineTest {

    private val minute = 60 * 1000L
    private val hour = 60 * minute
    private val from = 10 * hour
    private val profileTarget: (Long) -> Double = { 100.0 }

    private fun tt(start: Long, duration: Long, low: Double, high: Double) =
        TT(id = start, timestamp = start, reason = TT.Reason.CUSTOM, lowTarget = low, highTarget = high, duration = duration)

    @Test
    fun withoutATemporaryTargetTheLineIsTheProfileTargetFromStartToEnd() {
        val line = buildTargetLine(from, from + 2 * hour, emptyList(), profileTarget) { it }

        assertEquals(listOf(GraphDataPoint(from, 100.0), GraphDataPoint(from + 2 * hour, 100.0)), line)
    }

    @Test
    fun aTemporaryTargetMakesAStepUpAtItsStartAndDownAtItsEnd() {
        val line = buildTargetLine(from, from + 3 * hour, listOf(tt(from + hour, hour, 120.0, 140.0)), profileTarget) { it }

        assertEquals(
            listOf(
                GraphDataPoint(from, 100.0),
                GraphDataPoint(from + hour, 130.0),
                GraphDataPoint(from + 2 * hour, 100.0),
                GraphDataPoint(from + 3 * hour, 100.0)
            ),
            line
        )
    }

    /** The one running at the start counts from the start: its own start is before the range. */
    @Test
    fun aTemporaryTargetRunningAtTheStartIsTheFirstValue() {
        val line = buildTargetLine(from, from + 2 * hour, listOf(tt(from - hour, 2 * hour, 80.0, 80.0)), profileTarget) { it }

        assertEquals(listOf(GraphDataPoint(from, 80.0), GraphDataPoint(from + hour, 100.0), GraphDataPoint(from + 2 * hour, 100.0)), line)
    }

    /** An end in the future shows how long the temporary target lasts. */
    @Test
    fun aTemporaryTargetLastingPastTheEndKeepsItsValueToTheEnd() {
        val line = buildTargetLine(from, from + hour, listOf(tt(from + hour / 2, 5 * hour, 90.0, 90.0)), profileTarget) { it }

        assertEquals(listOf(GraphDataPoint(from, 100.0), GraphDataPoint(from + hour / 2, 90.0), GraphDataPoint(from + hour, 90.0)), line)
    }

    /** The steps stand where the temporary target starts and ends, not on the next five minutes of the clock. */
    @Test
    fun theStepsStandExactlyAtTheTemporaryTargetsStartAndEnd() {
        val start = from + 51 * minute
        val line = buildTargetLine(from, from + 2 * hour, listOf(tt(start, 6 * minute, 120.0, 140.0)), profileTarget) { it }

        assertEquals(
            listOf(
                GraphDataPoint(from, 100.0),
                GraphDataPoint(start, 130.0),
                GraphDataPoint(start + 6 * minute, 100.0),
                GraphDataPoint(from + 2 * hour, 100.0)
            ),
            line
        )
    }

    /** The phone and the watch build the line over different ranges and must get the same steps. */
    @Test
    fun twoRangesStartingAtDifferentTimesGiveTheSameSteps() {
        val targets = listOf(tt(from + 51 * minute, hour, 120.0, 140.0))

        val fromTheHour = buildTargetLine(from, from + 3 * hour, targets, profileTarget) { it }
        val fromSevenPast = buildTargetLine(from + 7 * minute, from + 3 * hour, targets, profileTarget) { it }

        assertEquals(fromTheHour.drop(1), fromSevenPast.drop(1))
    }

    @Test
    fun theValuesAreTurnedIntoTheUnitsAsked() {
        val line = buildTargetLine(from, from + hour, emptyList(), profileTarget) { it / 18.0 }

        assertEquals(100.0 / 18.0, line.first().value)
    }
}
