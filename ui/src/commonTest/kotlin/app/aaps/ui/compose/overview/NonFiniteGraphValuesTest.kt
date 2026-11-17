package app.aaps.ui.compose.overview

import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventGraphPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventType
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A NaN or infinite graph value is not drawn (Vico crashed on it, #5525), but it must still be
 * reported, or the bug that made it would never be found.
 */
class NonFiniteGraphValuesTest {

    private class RecordingFabricPrivacy : FabricPrivacy {

        val exceptions = mutableListOf<Throwable>()
        override fun setUserProperty(key: String, value: String) = Unit
        override fun logCustom(event: String) = Unit
        override fun logCustom(name: String, params: Map<String, Long>) = Unit
        override fun logMessage(message: String) = Unit
        override fun logException(throwable: Throwable) {
            exceptions += throwable
        }

        override fun fabricEnabled(): Boolean = true
        override fun logWearException(wearException: EventData.WearException) = Unit
    }

    private val fabricPrivacy = RecordingFabricPrivacy()
    private val sut = NonFiniteGraphValues(fabricPrivacy)

    private fun List<GraphDataPoint>.dropIn(series: String) = sut.drop(series, this, { it.timestamp }, { it.value })

    @Test
    fun cleanPointsAreReturnedAsTheyAreAndNothingIsReported() {
        val points = listOf(GraphDataPoint(1, 70.0), GraphDataPoint(2, 72.0))

        assertSame(points, points.dropIn("heart rate"))
        assertTrue(fabricPrivacy.exceptions.isEmpty())
    }

    @Test
    fun nanAndInfiniteValuesAreDroppedAndTheSeriesIsReported() {
        val points = listOf(
            GraphDataPoint(1, 70.0),
            GraphDataPoint(2, Double.NaN),
            GraphDataPoint(3, 72.0),
            GraphDataPoint(4, Double.POSITIVE_INFINITY),
            GraphDataPoint(5, Double.NEGATIVE_INFINITY)
        )

        assertEquals(listOf(GraphDataPoint(1, 70.0), GraphDataPoint(3, 72.0)), points.dropIn("heart rate"))
        assertEquals(1, fabricPrivacy.exceptions.size)
        val report = fabricPrivacy.exceptions.single()
        assertTrue(report is NonFiniteGraphValueException)
        // The series, how many and the first bad one: what is needed to find the source
        assertEquals("Graph series 'heart rate': 3 value(s) not drawn, not a finite number. First: NaN at 2", report.message)
    }

    @Test
    fun aSeriesIsReportedOnlyOnceButEachSeriesOnItsOwn() {
        val bad = listOf(GraphDataPoint(1, Double.NaN))

        // The graphs are rebuilt every few minutes, with the same stored value each time
        repeat(3) { bad.dropIn("heart rate") }
        bad.dropIn("steps")

        assertEquals(2, fabricPrivacy.exceptions.size)
        assertTrue(fabricPrivacy.exceptions[0].message!!.contains("'heart rate'"))
        assertTrue(fabricPrivacy.exceptions[1].message!!.contains("'steps'"))
    }

    @Test
    fun aMissingValueIsKept() {
        // Most therapy events have no glucose. That is not an error and must not be dropped.
        val events = listOf(
            TherapyEventGraphPoint(1, TherapyEventType.ANNOUNCEMENT, "note", 0, glucoseUserUnits = null),
            TherapyEventGraphPoint(2, TherapyEventType.FINGER_STICK, "bad", 0, glucoseUserUnits = Double.NaN)
        )

        val kept = sut.drop("therapy event glucose", events, { it.timestamp }, { it.glucoseUserUnits })

        assertEquals(listOf(events[0]), kept)
        assertEquals(1, fabricPrivacy.exceptions.size)
    }
}
