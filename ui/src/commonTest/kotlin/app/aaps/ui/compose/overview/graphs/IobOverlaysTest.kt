package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.ExtendedBolusGraphPoint
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The extended boluses on the IOB graph, see [processExtendedBoluses]. Manual boluses and SMBs are
 * not on the IOB graph, they are on the BG graph.
 */
class IobOverlaysTest {

    private val minTimestamp = 1_000_000_000L

    private fun minutes(count: Long) = minTimestamp + count * 60_000L

    private fun extended(atMinute: Long, durationMinutes: Long, amount: Double) =
        ExtendedBolusGraphPoint(minutes(atMinute), amount, rate = amount * 60 / durationMinutes, duration = durationMinutes * 60_000L, label = "$amount U")

    private fun process(vararg extended: ExtendedBolusGraphPoint) =
        processExtendedBoluses(TreatmentGraphData(emptyList(), emptyList(), extended.toList(), emptyList(), emptyList()), minTimestamp, minX = 0.0, maxX = 600.0)

    @Test
    fun `an extended bolus runs from its start to its end at its amount`() {
        assertEquals(listOf(Triple(20.0, 80.0, 2.0)), process(extended(20, 60, 2.0)))
    }

    @Test
    fun `an extended bolus is cut to the graph`() {
        assertEquals(listOf(Triple(570.0, 600.0, 1.5)), process(extended(570, 120, 1.5)))
    }

    @Test
    fun `an extended bolus outside the graph is left out`() {
        assertTrue(process(extended(700, 30, 1.0)).isEmpty())
    }
}
