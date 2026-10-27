package app.aaps.ui.compose.overview.graphs

import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The amount labels over the IOB and COB curves: where they go, and where each one is placed. */
class AmountLabelsTest {

    /** IOB every 5 minutes: 1.0 at minute 0 and 5, then 3.0 from minute 10 on (a bolus at minute 7). */
    private val curve = listOf(0.0 to 1.0, 5.0 to 1.0, 10.0 to 3.0, 15.0 to 2.8, 20.0 to 2.6)

    @Test
    fun theValueAtAStepIsTheHighestPointSoonAfterTheTreatment() {
        // The point at minute 10 already holds the bolus; the one before it does not
        assertEquals(3.0, curveValueAt(7.0, curve))
    }

    @Test
    fun aPointAtTheTreatmentTimeThatStillHoldsTheOldValueIsNotTheStep() {
        // COB: the point at minute 5, the carbs' own time, still reads 0; the jump to 40 comes at minute 10
        val cob = listOf(0.0 to 0.0, 5.0 to 0.0, 10.0 to 40.0, 15.0 to 39.0)

        assertEquals(40.0, curveValueAt(5.0, cob))
    }

    @Test
    fun inAGapOfTheCurveTheLastPointBeforeIsUsed() {
        // No point from minute 8 to 18; the one at minute 5 is close enough
        val gap = listOf(0.0 to 1.0, 5.0 to 1.0, 20.0 to 3.0)

        assertEquals(1.0, curveValueAt(8.0, gap))
    }

    @Test
    fun aheadOfTheCurveThereIsNoValue() {
        // The curve ends at minute 20: a treatment at 25 is not in it yet, however close
        assertNull(curveValueAt(25.0, curve))
    }

    @Test
    fun farFromEveryPointThereIsNoValue() {
        assertNull(curveValueAt(60.0, curve))
    }

    @Test
    fun aLabelStandsAtTheCurveWithItsFormattedAmount() {
        val labels = amountLabelsOn(curve, listOf(17.0 to 0.5, 7.0 to 2.0), Axis.Position.Vertical.Start) { "$it" }

        assertEquals(
            listOf(
                AmountLabel(7.0, 3.0, Axis.Position.Vertical.Start, "2.0"),
                AmountLabel(17.0, 2.6, Axis.Position.Vertical.Start, "0.5")
            ),
            labels
        )
    }

    @Test
    fun aTreatmentWithNoCurveNearItStandsAtItsOwnAmount() {
        // eCarbs ahead of the now line: the curve ends at minute 20, the entry is at minute 60
        assertEquals(
            listOf(AmountLabel(60.0, 2.0, Axis.Position.Vertical.Start, "2.0")),
            amountLabelsOn(curve, listOf(60.0 to 2.0), Axis.Position.Vertical.Start) { "$it" }
        )
    }

    // Placement. Canvas y grows downwards: top = 0, bottom = 100. Text 10 high, 2 from the curve, 2 between labels.

    private fun place(
        centersX: List<Float>,
        centersY: List<Float>,
        halfWidth: Float = 5f,
        top: Float = 0f,
        bottom: Float = 100f,
        taken: List<LabelPlace> = emptyList()
    ) =
        placeLabels(
            centersX = centersX, centersY = centersY, halfWidths = centersX.map { halfWidth },
            height = 10f, lift = 2f, gap = 2f, top = top, bottom = bottom, maxRows = 3, taken = taken
        )

    @Test
    fun aLabelStandsRightOverItsCurvePoint() {
        assertEquals(listOf(LabelPlace(5f, 38f, 15f, 48f)), place(listOf(10f), listOf(50f)))
    }

    @Test
    fun labelsThatDoNotTouchAllStandOverTheCurve() {
        val places = place(listOf(10f, 40f, 70f), listOf(50f, 50f, 50f))

        assertEquals(listOf(48f, 48f, 48f), places.map { it?.bottom })
    }

    @Test
    fun aLabelRunningIntoTheOneBeforeItGoesOneRowUp() {
        // The second starts at 13, inside the first's right edge 15 plus the gap: one row (10 + 2) higher
        val places = place(listOf(10f, 18f), listOf(50f, 50f))

        assertEquals(listOf(48f, 36f), places.map { it?.bottom })
    }

    @Test
    fun withNoRoomOverTheCurveALabelStandsUnderIt() {
        // Over the point at 5 the text would end above the top of the graph
        assertEquals(listOf(LabelPlace(5f, 7f, 15f, 17f)), place(listOf(10f), listOf(5f)))
    }

    @Test
    fun aLabelUnderTheCurveStaysClearOfOneOverALowerPointNextToIt() {
        // The first stands over its point at 30, filling 18..28. The second, at the top, has to go under
        // its point: 7..17 and 19..29 both touch the first, so it takes the third row down, 31..41.
        val places = place(listOf(10f, 14f), listOf(30f, 5f))

        assertEquals(listOf(LabelPlace(5f, 18f, 15f, 28f), LabelPlace(9f, 31f, 19f, 41f)), places)
    }

    @Test
    fun aLabelKeepsClearOfAMarkerBox() {
        // An SMB triangle fills 35..45 right over the curve point at 50. The first row over the point,
        // 38..48, is on it; the second, 26..36, comes within the gap of its top; the third, 14..24, is free.
        val smb = LabelPlace(0f, 35f, 20f, 45f)

        assertEquals(listOf(LabelPlace(5f, 14f, 15f, 24f)), place(listOf(10f), listOf(50f), taken = listOf(smb)))
    }

    @Test
    fun aMarkerBoxFarFromTheLabelChangesNothing() {
        val smb = LabelPlace(60f, 35f, 80f, 45f)

        assertEquals(listOf(LabelPlace(5f, 38f, 15f, 48f)), place(listOf(10f), listOf(50f), taken = listOf(smb)))
    }

    @Test
    fun withNoFreePlaceALabelIsLeftOut() {
        // A graph only 12 high: over the point there is no room, under it the first label takes the only place
        val places = place(listOf(10f, 12f), listOf(1f, 1f), bottom = 13f)

        assertEquals(listOf(LabelPlace(5f, 3f, 15f, 13f), null), places)
    }

    @Test
    fun noLabelsGiveNothing() {
        assertEquals(emptyList(), place(emptyList(), emptyList()))
    }
}
