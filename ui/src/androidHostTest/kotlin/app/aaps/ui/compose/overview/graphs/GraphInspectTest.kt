package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.CalibrationGraphPoint
import app.aaps.core.interfaces.overview.graph.CarbsGraphPoint
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.interfaces.overview.graph.TherapyEventGraphPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventType
import com.google.common.truth.Truth.assertThat
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
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
            predictions = emptyList(),
            iob = listOf(point(0, 1.0), point(5, 1.2), point(10, 1.4)),
            cob = listOf(point(5, 0.0)),
            actualBasal = listOf(point(-30, 0.8), point(4, 1.5), point(20, 0.0)),
            profileBasal = listOf(point(-60, 0.8)),
            boluses = emptyList(),
            carbs = emptyList(),
            calibrations = emptyList(),
            therapyEvents = emptyList()
        )

        assertThat(data.timestamp).isEqualTo(t0 + 5 * minute)
        assertThat(data.bg?.value).isEqualTo(5.5)
        assertThat(data.iob).isEqualTo(1.2)
        // 0 is a value, not missing data
        assertThat(data.cob).isEqualTo(0.0)
        // Basal is the last change before the reading
        assertThat(data.basal).isEqualTo(1.5)
        assertThat(data.tbrState).isEqualTo(TbrState.HIGH)
    }

    @Test
    fun `no reading within 5 minutes gives no glucose, and values at the finger's time`() {
        val data = inspectDataAt(
            timestamp = t0 + 30 * minute,
            bgReadings = readings,
            predictions = emptyList(),
            iob = listOf(point(30, 0.3)),
            cob = emptyList(),
            actualBasal = emptyList(),
            profileBasal = emptyList(),
            boluses = emptyList(),
            carbs = emptyList(),
            calibrations = emptyList(),
            therapyEvents = emptyList()
        )

        assertThat(data.bg).isNull()
        assertThat(data.timestamp).isEqualTo(t0 + 30 * minute)
        assertThat(data.iob).isEqualTo(0.3)
        assertThat(data.cob).isNull()
        assertThat(data.basal).isNull()
    }

    @Test
    fun `after the last reading the time snaps to a prediction point, without glucose`() {
        val predictions = listOf(15, 20, 25).map { BgDataPoint(t0 + it * minute, 7.0, BgRange.HIGH, BgType.IOB_PREDICTION) }

        val data = inspectDataAt(
            timestamp = t0 + 19 * minute,
            bgReadings = readings,
            predictions = predictions,
            iob = listOf(point(20, 0.9)),
            cob = emptyList(),
            actualBasal = emptyList(),
            profileBasal = emptyList(),
            boluses = emptyList(),
            carbs = emptyList(),
            calibrations = emptyList(),
            therapyEvents = emptyList()
        )

        assertThat(data.timestamp).isEqualTo(t0 + 20 * minute)
        assertThat(data.bg).isNull()
        assertThat(data.iob).isEqualTo(0.9)
    }

    @Test
    fun `the finger's x comes from its distance to the nearest point, clamped to the graph`() {
        val state = GraphInspectState().apply { pxPerX = 2f; minX = 0.0; maxX = 100.0 }
        // Vico snaps to the last point of the held graph, here at x = 60, drawn at 120 px
        val lastPoint = object : CartesianMarker.Target {
            override val x = 60.0
            override val canvasX = 120f
        }

        assertThat(state.fingerX(130f, lastPoint)).isEqualTo(65.0)
        assertThat(state.fingerX(300f, lastPoint)).isEqualTo(100.0)
        assertThat(state.fingerX(-500f, lastPoint)).isEqualTo(0.0)
        assertThat(state.fingerX(130f, null)).isNull()
        // Nothing drawn yet: the point itself, as before
        assertThat(GraphInspectState().fingerX(130f, lastPoint)).isEqualTo(60.0)
    }

    /**
     * The window is measured from the finger, not from the reading the pill snapped to. A marker is
     * drawn on the BG graph at its own time, so a treatment between two readings has to be listed
     * when it is held — it used to be dropped, which read as "I can see it but holding it shows
     * nothing".
     */
    @Test
    fun `only valid treatments at the snapped reading are listed, not those nearer the finger`() {
        val near = BolusGraphPoint(t0 + 6 * minute, 0.4, BolusType.SMB, isValid = true, label = "0.40 U")
        // Nearer the finger than `near`, but it belongs to the reading at minute 10
        val far = BolusGraphPoint(t0 + 8 * minute, 1.0, BolusType.NORMAL, isValid = true, label = "1.00 U")
        val invalid = BolusGraphPoint(t0 + 5 * minute, 2.0, BolusType.NORMAL, isValid = false, label = "2.00 U")
        val carbs = CarbsGraphPoint(t0 + 4 * minute, 20.0, isValid = true, label = "20 g")

        // The finger is at minute 7, so the pill snaps to the reading at minute 5
        val data = inspectDataAt(
            timestamp = t0 + 7 * minute,
            bgReadings = readings,
            predictions = emptyList(),
            iob = emptyList(),
            cob = emptyList(),
            actualBasal = emptyList(),
            profileBasal = emptyList(),
            boluses = listOf(near, far, invalid),
            carbs = listOf(carbs),
            calibrations = emptyList(),
            therapyEvents = emptyList()
        )

        assertThat(data.boluses).containsExactly(near)
        assertThat(data.carbs).containsExactly(carbs)
    }

    @Test
    fun `between two readings with an SMB each, only the SMB of the nearer one is listed`() {
        val smbAt5 = BolusGraphPoint(t0 + 5 * minute, 0.3, BolusType.SMB, isValid = true, label = "0.30 U")
        val smbAt10 = BolusGraphPoint(t0 + 10 * minute, 0.5, BolusType.SMB, isValid = true, label = "0.50 U")

        // The finger is at minute 7, so the pill snaps to the reading at minute 5 and lists its SMB
        val data = inspectDataAt(
            timestamp = t0 + 7 * minute,
            bgReadings = readings,
            predictions = emptyList(),
            iob = emptyList(),
            cob = emptyList(),
            actualBasal = emptyList(),
            profileBasal = emptyList(),
            boluses = listOf(smbAt5, smbAt10),
            carbs = emptyList(),
            calibrations = emptyList(),
            therapyEvents = emptyList()
        )

        assertThat(data.boluses).containsExactly(smbAt5)
    }

    @Test
    fun `a bolus in the middle of a gap longer than five minutes is listed at the nearer reading`() {
        // Readings 5:05 apart, as a drifting source delivers them. The bolus is 2:40 after the first
        // and 2:25 before the second, outside a fixed half interval of 2:30 from both.
        val first = BgDataPoint(t0, 5.0, BgRange.IN_RANGE, BgType.REGULAR)
        val second = BgDataPoint(t0 + 305_000L, 5.5, BgRange.IN_RANGE, BgType.REGULAR)
        val bolus = BolusGraphPoint(t0 + 160_000L, 1.0, BolusType.NORMAL, isValid = true, label = "1.00 U")

        fun dataAt(timestamp: Long) = inspectDataAt(
            timestamp = timestamp,
            bgReadings = listOf(first, second),
            predictions = emptyList(),
            iob = emptyList(),
            cob = emptyList(),
            actualBasal = emptyList(),
            profileBasal = emptyList(),
            boluses = listOf(bolus),
            carbs = emptyList(),
            calibrations = emptyList(),
            therapyEvents = emptyList()
        )

        assertThat(dataAt(second.timestamp).boluses).containsExactly(bolus)
        assertThat(dataAt(first.timestamp).boluses).isEmpty()
    }

    @Test
    fun `a calibration and a BG check at the snapped reading are listed with their value`() {
        val calibration = CalibrationGraphPoint(t0 + 6 * minute, 7.2, isValid = true, label = "7.2 mmol/L")
        val invalid = CalibrationGraphPoint(t0 + 6 * minute, 9.9, isValid = false, label = "9.9 mmol/L")
        val check = TherapyEventGraphPoint(t0 + 4 * minute, TherapyEventType.FINGER_STICK, "6.8 mmol/L", 0, 6.8)
        val note = TherapyEventGraphPoint(t0 + 4 * minute, TherapyEventType.GENERAL, "sensor changed", 0, null)
        val far = TherapyEventGraphPoint(t0 + 20 * minute, TherapyEventType.MBG, "5.0 mmol/L", 0, 5.0)

        // The finger is at minute 7, so the pill snaps to the reading at minute 5
        val data = inspectDataAt(
            timestamp = t0 + 7 * minute,
            bgReadings = readings,
            predictions = emptyList(),
            iob = emptyList(),
            cob = emptyList(),
            actualBasal = emptyList(),
            profileBasal = emptyList(),
            boluses = emptyList(),
            carbs = emptyList(),
            calibrations = listOf(calibration, invalid),
            therapyEvents = listOf(check, note, far)
        )

        assertThat(data.measured).containsExactly("7.2 mmol/L", "6.8 mmol/L")
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
        val marker = SilentInspectMarker
        state.fingerDown = true
        state.pointerDown = true

        // Finger past the readings, e.g. over the axis labels
        state.visibilityListener.onHidden(marker)
        assertThat(state.fingerDown).isTrue()

        state.pointerDown = false
        state.visibilityListener.onHidden(marker)
        assertThat(state.fingerDown).isFalse()
    }

    @Test
    fun `TBR state follows the overview rule`() {
        assertThat(tbrState(basal = 0.8, profileBasal = 0.8)).isEqualTo(TbrState.NONE)
        assertThat(tbrState(basal = 0.805, profileBasal = 0.8)).isEqualTo(TbrState.NONE)
        assertThat(tbrState(basal = 1.5, profileBasal = 0.8)).isEqualTo(TbrState.HIGH)
        assertThat(tbrState(basal = 0.4, profileBasal = 0.8)).isEqualTo(TbrState.LOW)
        assertThat(tbrState(basal = 0.0, profileBasal = 0.8)).isEqualTo(TbrState.LOW)
        assertThat(tbrState(basal = 0.8, profileBasal = null)).isEqualTo(TbrState.NONE)
    }
}
