package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.CarbsGraphPoint
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The values shown when holding the BG graph. */
class GraphInspectTest {

    private val minute = 60_000L
    private val t0 = 1_000_000_000_000L

    private fun bg(minutes: Int, value: Double = 6.0) = BgDataPoint(t0 + minutes * minute, value, BgRange.IN_RANGE, BgType.REGULAR)
    private fun point(minutes: Int, value: Double) = GraphDataPoint(t0 + minutes * minute, value)

    private val readings = listOf(bg(0, 5.0), bg(5, 5.5), bg(10, 6.0))

    @Test
    fun `snaps to the nearest reading and takes the values at its time`() {
        val data = inspectDataAt(
            timestamp = t0 + 6 * minute,
            bgReadings = readings,
            iob = listOf(point(0, 1.0), point(5, 1.2), point(10, 1.4)),
            cob = listOf(point(5, 0.0)),
            actualBasal = listOf(point(-30, 0.8), point(4, 1.5), point(20, 0.0)),
            boluses = emptyList(),
            carbs = emptyList()
        )

        assertThat(data.timestamp).isEqualTo(t0 + 5 * minute)
        assertThat(data.bg?.value).isEqualTo(5.5)
        assertThat(data.iob).isEqualTo(1.2)
        // COB 0 is a value, not missing: the switch decides whether it is shown
        assertThat(data.cob).isEqualTo(0.0)
        // Basal is the last change before the reading
        assertThat(data.basal).isEqualTo(1.5)
    }

    @Test
    fun `no reading within 5 minutes gives no glucose, and values at the finger's time`() {
        val data = inspectDataAt(
            timestamp = t0 + 30 * minute,
            bgReadings = readings,
            iob = listOf(point(30, 0.3)),
            cob = emptyList(),
            actualBasal = emptyList(),
            boluses = emptyList(),
            carbs = emptyList()
        )

        assertThat(data.bg).isNull()
        assertThat(data.timestamp).isEqualTo(t0 + 30 * minute)
        assertThat(data.iob).isEqualTo(0.3)
        assertThat(data.cob).isNull()
        assertThat(data.basal).isNull()
    }

    @Test
    fun `only valid treatments within half a reading interval belong to the reading`() {
        val near = BolusGraphPoint(t0 + 5 * minute + 60_000, 0.4, BolusType.SMB, isValid = true, label = "0.40 U")
        val far = BolusGraphPoint(t0 + 5 * minute + 4 * minute, 1.0, BolusType.NORMAL, isValid = true, label = "1.00 U")
        val invalid = BolusGraphPoint(t0 + 5 * minute, 2.0, BolusType.NORMAL, isValid = false, label = "2.00 U")
        val carbs = CarbsGraphPoint(t0 + 5 * minute - 60_000, 20.0, isValid = true, label = "20 g")

        val data = inspectDataAt(
            timestamp = t0 + 5 * minute,
            bgReadings = readings,
            iob = emptyList(),
            cob = emptyList(),
            actualBasal = emptyList(),
            boluses = listOf(near, far, invalid),
            carbs = listOf(carbs)
        )

        assertThat(data.boluses).containsExactly(near)
        assertThat(data.carbs).containsExactly(carbs)
    }

    @Test
    fun `COB is left out of the pill when it is 0 g`() {
        assertThat(shownCob(null)).isNull()
        assertThat(shownCob(0.0)).isNull()
        // Rounds to 0 g, so it would read "0 g"
        assertThat(shownCob(0.4)).isNull()
        assertThat(shownCob(0.6)).isEqualTo(1)
        assertThat(shownCob(23.7)).isEqualTo(24)
    }

    @Test
    fun `leaving the plotted area with the finger down keeps inspecting, lifting it ends`() {
        val state = GraphInspectState()
        val marker = GraphInspectMarker(state)
        state.fingerDown = true
        state.pointerDown = true

        // Vico hides the marker when the finger is past the readings, e.g. over the axis labels
        state.visibilityListener.onHidden(marker)
        assertThat(state.fingerDown).isTrue()

        state.pointerDown = false
        state.visibilityListener.onHidden(marker)
        assertThat(state.fingerDown).isFalse()
    }
}
