package app.aaps.ui.compose.overview.graphs

import app.aaps.core.data.ui.MarkerTier
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.CalibrationGraphPoint
import app.aaps.core.interfaces.overview.graph.CarbsGraphPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventGraphPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Placement of the treatment markers drawn inside the BG graph.
 *
 * The markers sit on the curve, so the height has to come from the readings around them. These pin
 * the parts that are easy to get wrong: a treatment between two readings, a treatment in a sensor
 * gap, both halves of one meal lining up into a diamond, and the sizes staying stable while the
 * graph is scrolled.
 */
class TreatmentMarkersTest {

    private val minTimestamp = 1_000_000_000L
    private val minY = 40.0
    private val maxY = 240.0
    private val gap = 3.0
    private val fallbackY = 48.0

    private fun minutes(count: Long) = minTimestamp + count * 60_000L

    private fun reading(atMinute: Long, value: Double) =
        BgDataPoint(minutes(atMinute), value, BgRange.IN_RANGE, BgType.REGULAR)

    private fun bolus(atMinute: Long, amount: Double, type: BolusType = BolusType.NORMAL, isValid: Boolean = true) =
        BolusGraphPoint(minutes(atMinute), amount, type, isValid, "$amount U")

    private fun carbs(atMinute: Long, amount: Double, isValid: Boolean = true) =
        CarbsGraphPoint(minutes(atMinute), amount, isValid, "$amount g")

    private fun calibration(atMinute: Long, value: Double, isValid: Boolean = true) =
        CalibrationGraphPoint(minutes(atMinute), value, isValid, "$value")

    private fun bgCheck(atMinute: Long, value: Double?) =
        TherapyEventGraphPoint(minutes(atMinute), TherapyEventType.FINGER_STICK, "check", 0, value)

    /** Readings every 5 minutes, rising by 10 each time: 100 at minute 0 up to 140 at minute 20. */
    private val readings = (0..4).map { reading(it * 5L, 100.0 + it * 10) }

    private fun markers(
        boluses: List<BolusGraphPoint> = emptyList(),
        carbsList: List<CarbsGraphPoint> = emptyList(),
        calibrations: List<CalibrationGraphPoint> = emptyList(),
        therapyEvents: List<TherapyEventGraphPoint> = emptyList(),
        bgReadings: List<BgDataPoint> = readings,
        maxX: Double = 60.0
    ) = treatmentMarkers(
        boluses = boluses,
        carbs = carbsList,
        calibrations = calibrations,
        therapyEvents = therapyEvents,
        readings = bgReadings,
        minTimestamp = minTimestamp,
        minX = 0.0,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        gap = gap,
        fallbackY = fallbackY
    )

    @Test
    fun insulinSitsJustAboveTheInterpolatedReading() {
        // Minute 7 is 2/5 of the way from 110 (minute 5) to 120 (minute 10) = 114
        val result = markers(boluses = listOf(bolus(7, 2.0)))

        assertEquals(1, result.insulin.size)
        assertEquals(7.0, result.insulin.first().x)
        assertEquals(114.0 + gap, result.insulin.first().y)
    }

    @Test
    fun carbsSitJustBelowTheInterpolatedReading() {
        val result = markers(carbsList = listOf(carbs(7, 30.0)))

        assertEquals(114.0 - gap, result.carbs.first().y)
    }

    /**
     * Both halves of one meal are placed around the same reading, the same small space on each
     * side, so the two triangles line up into one marker instead of two marks at different heights.
     */
    @Test
    fun aMealDrawsBothHalvesAroundTheSameHeight() {
        // Carbs first, bolus two minutes later: one meal, so both halves use the earlier height
        val result = markers(boluses = listOf(bolus(9, 2.0)), carbsList = listOf(carbs(7, 30.0)))

        assertEquals(114.0 + gap, result.insulin.first().y)
        assertEquals(114.0 - gap, result.carbs.first().y)
    }

    @Test
    fun treatmentsFarApartKeepTheirOwnHeight() {
        val result = markers(boluses = listOf(bolus(20, 2.0)), carbsList = listOf(carbs(0, 30.0)))

        assertEquals(140.0 + gap, result.insulin.first().y)
        assertEquals(100.0 - gap, result.carbs.first().y)
    }

    @Test
    fun aTreatmentWithNoReadingNearItFallsBackToTheBottom() {
        val result = markers(boluses = listOf(bolus(50, 2.0)))

        assertEquals(fallbackY + gap, result.insulin.first().y)
    }

    @Test
    fun aTreatmentInASensorGapTakesTheNearSideNotAnInterpolation() {
        // Readings at minute 0 and minute 60 only. A bolus at minute 55 is 5 minutes from the later
        // reading, so it belongs to that one — interpolating across the hour would place it at 150.
        val result = markers(
            boluses = listOf(bolus(55, 2.0)),
            bgReadings = listOf(reading(0, 100.0), reading(60, 200.0))
        )

        assertEquals(200.0 + gap, result.insulin.first().y)
    }

    @Test
    fun smbAndInvalidTreatmentsAreNotDrawn() {
        val result = markers(
            boluses = listOf(bolus(5, 0.2, type = BolusType.SMB), bolus(5, 2.0, isValid = false)),
            carbsList = listOf(carbs(5, 30.0, isValid = false))
        )

        assertTrue(result.insulin.isEmpty())
        assertTrue(result.carbs.isEmpty())
    }

    @Test
    fun treatmentsOutsideTheRangeAreNotDrawn() {
        val result = markers(boluses = listOf(bolus(5, 2.0), bolus(90, 2.0)), maxX = 60.0)

        assertEquals(listOf(5.0), result.insulin.map { it.x })
    }

    @Test
    fun markersComeOutSortedByTime() {
        val result = markers(boluses = listOf(bolus(20, 2.0), bolus(0, 2.0), bolus(10, 2.0)))

        assertEquals(listOf(0.0, 10.0, 20.0), result.insulin.map { it.x })
    }

    /**
     * The axis is scaled from the visible window, so a reading outside it can sit above or below
     * the axis. A marker on such a reading stays on the edge instead of being drawn off the graph.
     */
    @Test
    fun aMarkerNeverLeavesTheAxis() {
        val result = markers(
            boluses = listOf(bolus(0, 2.0)),
            carbsList = listOf(carbs(20, 30.0)),
            bgReadings = listOf(reading(0, 300.0), reading(20, 20.0))
        )

        assertEquals(maxY, result.insulin.first().y)
        assertEquals(minY, result.carbs.first().y)
    }

    @Test
    fun measuredValuesAreDrawnAtTheirOwnValue() {
        val result = markers(
            calibrations = listOf(calibration(5, 130.0)),
            therapyEvents = listOf(bgCheck(10, 150.0), bgCheck(15, null))
        )

        assertEquals(listOf(5.0 to 130.0, 10.0 to 150.0), result.calibrations)
    }

    @Test
    fun anInvalidCalibrationIsNotDrawn() {
        val result = markers(calibrations = listOf(calibration(5, 130.0, isValid = false)))

        assertTrue(result.calibrations.isEmpty())
    }

    @Test
    fun aMeasuredValueOffTheAxisIsClampedToIt() {
        val result = markers(calibrations = listOf(calibration(5, 400.0)))

        assertEquals(listOf(5.0 to maxY), result.calibrations)
    }

    @Test
    fun theSizeComesFromEveryLoadedMarkerNotOnlyTheVisibleOnes() {
        // 1 U at minute 5 is the smallest of the three, and stays the smallest when the two larger
        // ones are scrolled out of the window
        val all = listOf(bolus(5, 1.0), bolus(70, 5.0), bolus(80, 9.0))

        val wide = markers(boluses = all, maxX = 120.0)
        val narrow = markers(boluses = all, maxX = 60.0)

        assertEquals(listOf(MarkerTier.SMALL, MarkerTier.MEDIUM, MarkerTier.LARGE), wide.insulin.map { it.tier })
        assertEquals(listOf(MarkerTier.SMALL), narrow.insulin.map { it.tier })
    }

    /**
     * Measured from zero, so two doses that are nearly the same look the same. Measuring from the
     * smallest dose instead drew a 0.20 U bolus as the smallest marker and a 0.25 U bolus next to
     * it as the largest.
     */
    @Test
    fun twoAlmostEqualAmountsGetTheSameSize() {
        val result = markers(boluses = listOf(bolus(0, 0.20), bolus(5, 0.25)))

        assertEquals(listOf(MarkerTier.LARGE, MarkerTier.LARGE), result.insulin.map { it.tier })
    }

    @Test
    fun equalAmountsAllGetTheSameSize() {
        val result = markers(boluses = listOf(bolus(0, 2.0), bolus(5, 2.0)))

        assertEquals(listOf(MarkerTier.LARGE, MarkerTier.LARGE), result.insulin.map { it.tier })
    }

    @Test
    fun aSmallCorrectionNextToAMealBolusStaysSmall() {
        val result = markers(boluses = listOf(bolus(0, 1.0), bolus(5, 10.0)))

        assertEquals(listOf(MarkerTier.SMALL, MarkerTier.LARGE), result.insulin.map { it.tier })
    }

    @Test
    fun thereIsNoHeightWithoutReadings() {
        assertNull(interpolatedBgAt(minutes(5), emptyList()))
    }

    @Test
    fun aReadingAtTheSameTimeGivesItsOwnValue() {
        assertEquals(110.0, interpolatedBgAt(minutes(5), readings))
    }

    @Test
    fun readingsInAnyOrderGiveTheSameHeight() {
        assertEquals(114.0, interpolatedBgAt(minutes(7), readings.reversed()))
    }
}
