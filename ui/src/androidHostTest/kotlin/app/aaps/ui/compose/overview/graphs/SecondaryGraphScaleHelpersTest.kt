package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.DeviationType
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.SeriesType
import com.google.common.truth.Truth.assertThat
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartRanges
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Regression coverage for the axis and windowing helpers of [SecondaryGraphCompose] and
 * SecondaryGraphAxes.kt. Each test ties back to a specific bug or rule — see the KDoc on the
 * function under test for the full rationale.
 */
internal class SecondaryGraphScaleHelpersTest {

    @Nested
    inner class WindowedYTest {

        @Test
        fun `filters points to the visible window`() {
            val points = listOf(0.0 to 1.0, 5.0 to 2.0, 10.0 to 3.0)
            assertThat(windowedY(points, 4.0, 11.0)).containsExactly(2.0, 3.0)
        }

        @Test
        fun `falls back to the full list when the window has no points`() {
            val points = listOf(0.0 to 1.0, 5.0 to 2.0)
            assertThat(windowedY(points, 100.0, 200.0)).containsExactly(1.0, 2.0)
        }

        @Test
        fun `null window bounds return every point unfiltered`() {
            val points = listOf(0.0 to 1.0, 5.0 to 2.0)
            assertThat(windowedY(points, null, null)).containsExactly(1.0, 2.0)
        }
    }

    @Nested
    inner class StepValueAtTest {

        @Test
        fun `returns the value in effect at x from the most recent point at-or-before it`() {
            val points = listOf(0.0 to 1.0, 60.0 to 2.0, 120.0 to 3.0)
            assertThat(stepValueAt(points, 90.0)).isEqualTo(2.0)
        }

        @Test
        fun `a point exactly at x counts as in effect`() {
            val points = listOf(0.0 to 1.0, 60.0 to 2.0, 120.0 to 3.0)
            assertThat(stepValueAt(points, 60.0)).isEqualTo(2.0)
        }

        @Test
        fun `falls back to the first point when x precedes all data`() {
            // The basal-profile-in-a-narrow-zoom-window fix: even scrolled before any recorded
            // change point, the series still has a well-defined value.
            val points = listOf(60.0 to 2.0, 120.0 to 3.0)
            assertThat(stepValueAt(points, 10.0)).isEqualTo(2.0)
        }

        @Test
        fun `empty series has no value in effect`() {
            assertThat(stepValueAt(emptyList(), 10.0)).isNull()
        }
    }

    @Nested
    inner class ProcessStepPointsTest {

        /** x is whole minutes from minTimestamp, so a minute count is the readable way to write one. */
        private fun min(m: Long) = m * 60_000L

        @Test
        fun `a closing point past the right edge is clamped to the edge, not dropped`() {
            // The reported "missing basal" bug. rebuildBasalGraph closes the series at the newest
            // prediction time, which is hours past an axis that ends at TimeRange.toTime while the
            // predictions overlay is off. Dropping that point left the line stopping at the last
            // basal change instead of at the edge.
            val points = listOf(
                GraphDataPoint(min(0), 1.0),
                GraphDataPoint(min(30), 2.0),
                GraphDataPoint(min(300), 2.0) // closing point, far past the edge
            )
            val result = processStepPoints(points, minTimestamp = 0L, minX = 0.0, maxX = 60.0)
            assertThat(result).containsExactly(0.0 to 1.0, 30.0 to 2.0, 60.0 to 2.0).inOrder()
        }

        @Test
        fun `the clamped point carries the value in effect at the edge, not the last value overall`() {
            val points = listOf(
                GraphDataPoint(min(0), 1.0),
                GraphDataPoint(min(30), 2.0),
                GraphDataPoint(min(90), 3.0), // a change after the edge must not leak back into view
                GraphDataPoint(min(300), 3.0)
            )
            val result = processStepPoints(points, minTimestamp = 0L, minX = 0.0, maxX = 60.0)
            assertThat(result).containsExactly(0.0 to 1.0, 30.0 to 2.0, 60.0 to 2.0).inOrder()
        }

        @Test
        fun `a series that already ends exactly at the edge is left alone`() {
            val points = listOf(GraphDataPoint(min(0), 1.0), GraphDataPoint(min(60), 1.0))
            val result = processStepPoints(points, minTimestamp = 0L, minX = 0.0, maxX = 60.0)
            assertThat(result).containsExactly(0.0 to 1.0, 60.0 to 1.0).inOrder()
        }

        @Test
        fun `an empty series stays empty`() {
            assertThat(processStepPoints(emptyList(), minTimestamp = 0L, minX = 0.0, maxX = 60.0)).isEmpty()
        }

        @Test
        fun `a series lying entirely past the edge contributes nothing`() {
            val points = listOf(GraphDataPoint(min(90), 1.0), GraphDataPoint(min(120), 2.0))
            assertThat(processStepPoints(points, minTimestamp = 0L, minX = 0.0, maxX = 60.0)).isEmpty()
        }
    }

    @Nested
    inner class WindowedValuesTest {

        private fun spec(vararg points: Pair<Double, Double>, slot: SeriesSlot = SeriesSlot.IobLine, countsForRange: Boolean = true) =
            SeriesSpec(points.map { it.first }, points.map { it.second }, slot, countsForRange)

        @Test
        fun `unions the values of the series of one axis, windowed`() {
            val deviations = SeriesSpec(listOf(0.0, 10.0), listOf(3.0, 4.0), SeriesSlot.DeviationLine(DeviationType.POSITIVE))
            val bgi = spec(0.0 to 7.0, 10.0 to 8.0, slot = SeriesSlot.SimpleLine(SeriesType.BGI))

            assertThat(windowedValues(listOf(deviations, bgi), 5.0, 10.0)).containsExactly(4.0, 8.0)
        }

        @Test
        fun `a series with nothing in-window contributes nothing, without triggering a whole-set fallback`() {
            // Windowed points from one series are never mixed with unwindowed points from another.
            // IOB has no point in [5,10]; COB does. The union as a whole is non-empty (thanks to COB),
            // so IOB must NOT fall back to its own full range.
            val iob = spec(0.0 to 1.0, 2.0 to 1.5)
            val cob = spec(0.0 to 5.0, 10.0 to 6.0, slot = SeriesSlot.CobLine)

            assertThat(windowedValues(listOf(iob, cob), 5.0, 10.0)).containsExactly(6.0)
        }

        @Test
        fun `falls back to the full union only when every series is empty in-window`() {
            val iob = spec(0.0 to 1.0, 10.0 to 2.0)
            val cob = spec(0.0 to 5.0, 10.0 to 6.0, slot = SeriesSlot.CobLine)

            assertThat(windowedValues(listOf(iob, cob), 1000.0, 2000.0)).containsExactly(1.0, 2.0, 5.0, 6.0)
        }

        // The activity overlay is drawn in IOB units, scaled from the IOB axis: counting it would be circular
        @Test
        fun `a series drawn in the units of another one does not count`() {
            val iob = spec(0.0 to 1.0, 10.0 to 2.0)
            val overlay = spec(0.0 to 9.0, slot = SeriesSlot.ActivityOverlay, countsForRange = false)

            assertThat(windowedValues(listOf(iob, overlay), null, null)).containsExactly(1.0, 2.0)
        }
    }

    @Nested
    inner class GraphSidesTest {

        /** The series a user can put on a graph below BG. */
        private val configurable = SeriesType.entries - SeriesType.IOB - SeriesType.PREDICTIONS

        // #5376: DEV was moved to the left, COB drew a plain line on the right and the numbers were DEV's
        @Test
        fun `the first series takes the left axis and the second the right one`() {
            assertThat(graphSides(listOf(SeriesType.COB, SeriesType.DEVIATIONS))).isEqualTo(GraphSides(listOf(SeriesType.COB), listOf(SeriesType.DEVIATIONS)))
            assertThat(graphSides(listOf(SeriesType.DEVIATIONS, SeriesType.COB))).isEqualTo(GraphSides(listOf(SeriesType.DEVIATIONS), listOf(SeriesType.COB)))
        }

        @Test
        fun `two series with the same unit share the left axis`() {
            assertThat(graphSides(listOf(SeriesType.BGI, SeriesType.DEVIATIONS))).isEqualTo(GraphSides(listOf(SeriesType.BGI, SeriesType.DEVIATIONS), emptyList()))
            assertThat(graphSides(listOf(SeriesType.DEVIATIONS, SeriesType.BGI))).isEqualTo(GraphSides(listOf(SeriesType.DEVIATIONS, SeriesType.BGI), emptyList()))
        }

        @Test
        fun `one series takes the left axis`() {
            assertThat(graphSides(listOf(SeriesType.IOB))).isEqualTo(GraphSides(listOf(SeriesType.IOB), emptyList()))
        }

        @Test
        fun `every pair keeps both series, the first one on the left`() {
            for (first in configurable) for (second in configurable - first) {
                val sides = graphSides(listOf(first, second))
                assertThat(sides.left.first()).isEqualTo(first)
                assertThat(sides.left + sides.right).containsExactly(first, second)
            }
        }
    }

    @Nested
    inner class AxisRangeTest {

        @Test
        fun `a zero-floor axis starts at zero`() {
            assertThat(axisRange(AxisRule.ZeroFloor, listOf(12.0, 82.0))).isEqualTo(AxisRange(0.0, 90.0, 0.0))
        }

        @Test
        fun `a zero-floor axis keeps a small negative part`() {
            assertThat(axisRange(AxisRule.ZeroFloor, listOf(-0.5, 12.0))).isEqualTo(AxisRange(-0.5, 12.0, 0.0))
        }

        // BGI is mostly below zero: its axis has to show the zero it is measured from
        @Test
        fun `a zero-floor axis of values all below zero still reaches zero`() {
            val range = axisRange(AxisRule.ZeroFloor, listOf(-10.0, -2.0))!!

            assertThat(range.min).isEqualTo(-10.0)
            // Rounding up to the grid gives -0.0 here, which is the same number
            assertThat(range.max).isWithin(0.0).of(0.0)
        }

        @Test
        fun `a pivot axis is centered on its pivot`() {
            assertThat(axisRange(axisRuleOf(SeriesType.SENSITIVITY), listOf(60.0, 108.0))).isEqualTo(AxisRange(60.0, 140.0, 100.0))
        }

        @Test
        fun `a free axis follows the values only`() {
            assertThat(axisRange(axisRuleOf(SeriesType.HEART_RATE), listOf(62.0, 118.0))).isEqualTo(AxisRange(60.0, 120.0, null))
        }

        @Test
        fun `no values, no range`() {
            assertThat(axisRange(AxisRule.ZeroFloor, emptyList())).isNull()
        }
    }

    @Nested
    inner class AlignReferencesTest {

        private fun AxisBounds.fractionOf(value: Double) = (value - min) / (max - min)

        @Test
        fun `both zeros meet halfway, one axis stretched down and the other up`() {
            val cob = AxisRange(0.0, 90.0, 0.0)
            val deviations = AxisRange(-10.0, 20.0, 0.0)

            val (left, right) = alignReferences(cob, deviations)

            assertThat(left.min).isWithin(1e-9).of(-18.0)
            assertThat(left.max).isEqualTo(90.0)
            assertThat(right.min).isEqualTo(-10.0)
            assertThat(right.max).isWithin(1e-9).of(50.0)
        }

        @Test
        fun `a pivot meets the zero of the other axis`() {
            val sensitivity = AxisRange(60.0, 140.0, 100.0)
            val cob = AxisRange(0.0, 90.0, 0.0)

            val (left, right) = alignReferences(sensitivity, cob)

            assertThat(left.fractionOf(100.0)).isWithin(1e-9).of(right.fractionOf(0.0))
        }

        @Test
        fun `the references end on one height and nothing is cut off`() {
            val ranges = listOf(
                AxisRange(0.0, 90.0, 0.0), AxisRange(-10.0, 20.0, 0.0), AxisRange(-10.0, 0.0, 0.0),
                AxisRange(60.0, 140.0, 100.0), AxisRange(-0.5, 12.0, 0.0), AxisRange(-3.0, 3.0, 0.0)
            )
            for (a in ranges) for (b in ranges) {
                val (left, right) = alignReferences(a, b)
                assertThat(left.fractionOf(a.reference!!)).isWithin(1e-9).of(right.fractionOf(b.reference!!))
                assertThat(left.min).isAtMost(a.min)
                assertThat(left.max).isAtLeast(a.max)
                assertThat(right.min).isAtMost(b.min)
                assertThat(right.max).isAtLeast(b.max)
            }
        }

        // A heart rate of 60..120 has no zero worth lining up with anything
        @Test
        fun `an axis without a reference is not moved`() {
            val heartRate = AxisRange(60.0, 120.0, null)
            val cob = AxisRange(0.0, 90.0, 0.0)

            assertThat(alignReferences(heartRate, cob)).isEqualTo(heartRate.bounds to cob.bounds)
        }
    }

    @Nested
    inner class GraphAxesTest {

        @Test
        fun `a graph without values gets an empty frame`() {
            assertThat(graphAxes(null, null, basalBand = false)).isEqualTo(GraphAxes(EMPTY_AXIS, null))
        }

        @Test
        fun `the IOB graph leaves the top of its height to the basal band`() {
            val axes = graphAxes(AxisRange(0.0, 3.0, 0.0), null, basalBand = true)

            assertThat(axes.left).isEqualTo(AxisBounds(0.0, 3.0 / IOB_HEIGHT_FRACTION))
            assertThat(axes.right).isNull()
        }

        @Test
        fun `one series keeps its own range`() {
            assertThat(graphAxes(AxisRange(0.0, 90.0, 0.0), null, basalBand = false)).isEqualTo(GraphAxes(AxisBounds(0.0, 90.0), null))
        }

        @Test
        fun `two series are lined up at their zeros`() {
            val cob = AxisRange(0.0, 90.0, 0.0)
            val deviations = AxisRange(-10.0, 20.0, 0.0)

            val (left, right) = alignReferences(cob, deviations)

            assertThat(graphAxes(cob, deviations, basalBand = false)).isEqualTo(GraphAxes(left, right))
        }
    }

    @Nested
    inner class NiceVerticalAxisItemPlacerTest {

        /** A drawing context whose start axis goes from [minY] to [maxY]. */
        private fun context(minY: Double, maxY: Double): CartesianDrawingContext {
            val yRange = mock<CartesianChartRanges.YRange> {
                on { this.minY } doReturn minY
                on { this.maxY } doReturn maxY
            }
            val ranges = mock<CartesianChartRanges> { on { getYRange(any()) } doReturn yRange }
            return mock { on { this.ranges } doReturn ranges }
        }

        @Test
        fun `labels come from the axis range and stop at labelMax`() {
            // The IOB/BAS axis of a 2.9 U IOB: 0..4, the IOB itself up to 3
            val placer = NiceVerticalAxisItemPlacer(labelMax = 3.0)

            assertThat(placer.getLabelValues(context(0.0, 4.0), 200f, 20f, Axis.Position.Vertical.Start)).containsExactly(0.0, 1.0, 2.0, 3.0).inOrder()
        }

        @Test
        fun `labelMin hides a fake negative label`() {
            val placer = NiceVerticalAxisItemPlacer(labelMin = 0.0)

            assertThat(placer.getLabelValues(context(-82.0, 82.0), 200f, 20f, Axis.Position.Vertical.Start)).containsExactly(0.0, 50.0).inOrder()
        }

        @Test
        fun `the axis is measured for its width with the same labels it draws`() {
            val placer = NiceVerticalAxisItemPlacer(labelMax = 3.0)
            val context = context(0.0, 4.0)

            assertThat(placer.getWidthMeasurementLabelValues(context, 200f, 20f, Axis.Position.Vertical.Start))
                .isEqualTo(placer.getLabelValues(context, 200f, 20f, Axis.Position.Vertical.Start))
        }

        @Test
        fun `grid lines are left to Vico, which draws them at the labels`() {
            assertThat(NiceVerticalAxisItemPlacer().getLineValues(context(0.0, 4.0), 200f, 20f, Axis.Position.Vertical.Start)).isNull()
        }

        // The placer is part of the extras of the chart model: equal placers must not make Vico redraw
        @Test
        fun `placers with the same limits are equal`() {
            assertThat(NiceVerticalAxisItemPlacer(labelMax = 3.0)).isEqualTo(NiceVerticalAxisItemPlacer(labelMax = 3.0))
            assertThat(NiceVerticalAxisItemPlacer(labelMax = 3.0)).isNotEqualTo(NiceVerticalAxisItemPlacer(labelMax = 4.0))
        }

        // Seen on the phone: an empty heart rate graph showed 0, 0.5 and 1 next to a series on the right,
        // which read as that series' scale
        @Test
        fun `an axis without values shows no labels but keeps its width`() {
            val context = context(0.0, 1.0)

            assertThat(NoLabelsAxisItemPlacer.getLabelValues(context, 200f, 20f, Axis.Position.Vertical.Start)).isEmpty()
            assertThat(NoLabelsAxisItemPlacer.getWidthMeasurementLabelValues(context, 200f, 20f, Axis.Position.Vertical.Start)).containsExactly(0.0)
        }
    }
}
