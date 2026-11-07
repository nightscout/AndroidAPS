package app.aaps.ui.compose.overview.graphs

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Regression coverage for the axis scaling math: the bounds, rounded out a little from the data,
 * and the round label values, which are placed apart from the bounds. Expected values worked out
 * by hand from the functions, not guessed, and pinned here so a later change cannot quietly
 * break them.
 */
internal class GraphUtilsTest {

    /**
     * #5418: the "now + 2 h" scroll after a reset waited for a scroll limit above 0. With an axis that fits in the
     * start window that limit stays 0, so the wait never ended and the scroll came later, during a pinch.
     */
    @Nested
    inner class AxisLongerThanTest {

        private val start = 1_700_000_000_000L
        private val minute = 60_000L

        @Test
        fun `an axis longer than the window can scroll`() {
            assertThat(axisLongerThan(start to start + 361 * minute, DEFAULT_GRAPH_ZOOM_MINUTES)).isTrue()
        }

        @Test
        fun `an axis that fits in the window, as with a short history, cannot`() {
            assertThat(axisLongerThan(start to start + 360 * minute, DEFAULT_GRAPH_ZOOM_MINUTES)).isFalse()
            assertThat(axisLongerThan(start to start + 75 * minute, DEFAULT_GRAPH_ZOOM_MINUTES)).isFalse()
        }
    }

    @Nested
    inner class NiceScaleTest {

        @ParameterizedTest(name = "max {0} rounds out to {1}")
        @CsvSource(
            "195.0, 200.0",
            "210.0, 220.0",
            "250.0, 260.0",
            "310.0, 350.0",
            // IOB. With the labels as the bounds, 2.9 U got an axis up to 4 U
            "2.9, 3.0",
            "2.1, 2.2",
            "1.05, 1.1",
            "0.6, 0.6",
        )
        fun `the max rounds out to the next multiple of about a tenth of the range`(input: Double, expectedMax: Double) {
            val scale = niceScale(0.0, input)
            assertThat(scale.min).isEqualTo(0.0)
            assertThat(scale.max).isWithin(TOLERANCE).of(expectedMax)
        }

        @Test
        fun `a bound lies at most a sixth of the range beyond the data, and never inside it`() {
            for (max in listOf(0.13, 0.6, 1.05, 2.1, 2.9, 4.4, 7.3, 9.9, 30.1, 55.0, 148.0, 210.0, 399.0)) {
                val scale = niceScale(0.0, max)
                assertWithMessage("max=$max").that(scale.max).isAtLeast(max)
                assertWithMessage("max=$max").that(scale.max - max).isAtMost(max / 6.0 + TOLERANCE)
            }
        }

        @Test
        fun `never clips the real data range`() {
            val scale = niceScale(12.39, 57.39)
            assertThat(scale.min).isEqualTo(10.0)
            assertThat(scale.max).isEqualTo(60.0)
        }

        @Test
        fun `degenerate range (min equals max) still produces a usable non-empty scale`() {
            val scale = niceScale(5.0, 5.0)
            assertThat(scale.max).isGreaterThan(scale.min)
        }
    }

    @Nested
    inner class NiceUpTest {

        @Test
        fun `non-positive values return zero`() {
            assertThat(niceUp(0.0)).isEqualTo(0.0)
            assertThat(niceUp(-5.0)).isEqualTo(0.0)
        }

        @Test
        fun `rounds up, never down, and never below the input`() {
            assertThat(niceUp(82.0)).isEqualTo(90.0)
            assertThat(niceUp(82.0)).isAtLeast(82.0)
        }

        @Test
        fun `a value already on its grid is left unchanged`() {
            // 0.05 / 0.005 comes out a hair above 10, and must not be rounded up to 0.055
            assertThat(niceUp(0.05)).isWithin(TOLERANCE).of(0.05)
        }
    }

    @Nested
    inner class NiceScaleAroundPivotTest {

        @Test
        fun `pivot always sits exactly at the midpoint regardless of input asymmetry`() {
            val scale = niceScaleAroundPivot(min = 60.0, max = 108.0, pivot = 100.0)
            assertThat(scale.min).isEqualTo(60.0)
            assertThat(scale.max).isEqualTo(140.0)
            assertThat((scale.min + scale.max) / 2.0).isEqualTo(100.0)
        }

        @Test
        fun `never clips the real data range`() {
            val scale = niceScaleAroundPivot(min = 60.0, max = 108.0, pivot = 100.0)
            assertThat(scale.min).isAtMost(60.0)
            assertThat(scale.max).isAtLeast(108.0)
        }

        @Test
        fun `DEV_SLOPE-style pivot at zero centers correctly`() {
            val scale = niceScaleAroundPivot(min = -3.0, max = 1.0, pivot = 0.0)
            assertThat(scale.min).isWithin(TOLERANCE).of(-3.0)
            assertThat(scale.max).isWithin(TOLERANCE).of(3.0)
        }

        @Test
        fun `SENS sitting flat on its pivot snaps to a fixed 95-100-105 scale, not a tight near-zero one`() {
            // Regression for the reported bug: a constant SENS at 100% used to produce something
            // like 99/99.5/100/100.5/101 instead of a clean 95%/100%/105%.
            val scale = niceScaleAroundPivot(min = 100.0, max = 100.0, pivot = 100.0, minDeviation = 5.0)
            assertThat(scale.min).isEqualTo(95.0)
            assertThat(scale.max).isEqualTo(105.0)
        }

        @Test
        fun `deviation above the minDeviation floor ignores the floor and rounds normally`() {
            val scale = niceScaleAroundPivot(min = 40.0, max = 160.0, pivot = 100.0, minDeviation = 5.0)
            assertThat(scale.min).isEqualTo(40.0)
            assertThat(scale.max).isEqualTo(160.0)
        }
    }

    @Nested
    inner class ZeroFloorNiceRangeTest {

        @Test
        fun `all-positive data floors at exactly zero`() {
            val scale = zeroFloorNiceRange(dataMin = 5.0, dataMax = 82.0)
            assertThat(scale.min).isEqualTo(0.0)
            assertThat(scale.max).isEqualTo(90.0)
        }

        @Test
        fun `tiny negative excursion relative to a large positive side gets its own independent sliver`() {
            // ratio (8.0 / 0.05 = 160) is far above the default disparityRatio of 10 — the negative
            // sliver must stay tight (its own grid), not rounded out to the grid of the whole range.
            val scale = zeroFloorNiceRange(dataMin = -0.05, dataMax = 8.0)
            assertThat(scale.min).isWithin(TOLERANCE).of(-0.05)
            assertThat(scale.max).isEqualTo(8.0)
        }

        /**
         * The IOB of the screenshot in PR #5352: a hair of negative IOB in view took this branch, which
         * rounded 2.9 U up to 5 U, so IOB used little more than half of its own part of the graph.
         */
        @Test
        fun `a little negative IOB does not blow the top up`() {
            val scale = zeroFloorNiceRange(dataMin = -0.05, dataMax = 2.9)
            assertThat(scale.min).isWithin(TOLERANCE).of(-0.05)
            assertThat(scale.max).isWithin(TOLERANCE).of(3.0)
        }

        @Test
        fun `a negative sliver is rounded on its own grid`() {
            val scale = zeroFloorNiceRange(dataMin = -0.3, dataMax = 8.0)
            assertThat(scale.min).isWithin(TOLERANCE).of(-0.3)
            assertThat(scale.max).isEqualTo(8.0)
        }

        @Test
        fun `comparable-magnitude negative and positive are rounded on one grid`() {
            val scale = zeroFloorNiceRange(dataMin = -4.0, dataMax = 6.0)
            assertThat(scale.min).isEqualTo(-4.0)
            assertThat(scale.max).isEqualTo(6.0)
        }

        @Test
        fun `right at the disparity ratio boundary takes the independent-sliver branch`() {
            // ratio == disparityRatio exactly (10.0 / 1.0 = 10.0) must take the ">=" sliver branch,
            // not silently fall through to the unified one.
            val scale = zeroFloorNiceRange(dataMin = -1.0, dataMax = 10.0)
            assertThat(scale.min).isEqualTo(-1.0)
            assertThat(scale.max).isEqualTo(10.0)
        }

        @Test
        fun `never clips real data even when the negative side is larger than the positive side`() {
            val scale = zeroFloorNiceRange(dataMin = -12.0, dataMax = 3.0)
            assertThat(scale.min).isAtMost(-12.0)
            assertThat(scale.max).isAtLeast(3.0)
        }
    }

    @Nested
    inner class NiceAxisLabelValuesTest {

        /** The IOB axis of a 2.9 U IOB: top 3 U, divided by IOB_HEIGHT_FRACTION. Labels stop at the IOB top. */
        private fun iobLabels(axisHeight: Float) = niceAxisLabelValues(0.0, 4.0, axisHeight, labelHeight = 20f, labelMax = 3.0)

        @Test
        fun `a short graph gets a coarse step, a tall one a fine step`() {
            assertThat(iobLabels(100f)).containsExactly(0.0, 2.0).inOrder()
            assertThat(iobLabels(200f)).containsExactly(0.0, 1.0, 2.0, 3.0).inOrder()
            assertThat(iobLabels(400f)).containsExactly(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0).inOrder()
        }

        @Test
        fun `labels are round multiples of the step, not steps counted from the bound`() {
            // A BG axis of 3.5..10 mmol/L
            assertThat(niceAxisLabelValues(3.5, 10.0, axisHeight = 300f, labelHeight = 20f))
                .containsExactly(4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0).inOrder()
        }

        @Test
        fun `zero is a label on an axis with a negative sliver, and nothing below it`() {
            assertThat(niceAxisLabelValues(-0.05, 4.0, axisHeight = 100f, labelHeight = 20f, labelMax = 3.0)).containsExactly(0.0, 2.0).inOrder()
        }

        @Test
        fun `the SENS pivot of 100 percent is a label`() {
            assertThat(niceAxisLabelValues(95.0, 105.0, axisHeight = 100f, labelHeight = 20f)).containsExactly(95.0, 100.0, 105.0).inOrder()
        }

        @Test
        fun `labelMin keeps labels off the part below the series' own floor`() {
            // COB pushed into -82..82 to line its zero up with a pivot series
            assertThat(niceAxisLabelValues(-82.0, 82.0, axisHeight = 200f, labelHeight = 20f, labelMin = 0.0)).containsExactly(0.0, 50.0).inOrder()
        }

        /**
         * Measured on the Pixel 9a: the 100 dp IOB graph is 148 px high and a label is 42 px. An IOB of
         * 4.4 U has its top at 4.5 and its axis at 6, so the step for the full spacing is 5, and the
         * labels stop at 4.5 - only "0" was left.
         */
        @Test
        fun `labels come closer rather than leave a single one`() {
            assertThat(niceAxisLabelValues(0.0, 6.0, axisHeight = 148f, labelHeight = 42f, labelMax = 4.5)).containsExactly(0.0, 2.0, 4.0).inOrder()
            // And a high IOB of 9.4 U, top 9.5
            assertThat(niceAxisLabelValues(0.0, 9.5 / IOB_HEIGHT_FRACTION, axisHeight = 148f, labelHeight = 42f, labelMax = 9.5)).containsExactly(0.0, 5.0).inOrder()
        }

        @Test
        fun `labels never overlap, and an axis from zero always gets at least two`() {
            // From zero: IOB-like, with labels to the top or, as on the IOB/BAS graph, to the IOB top only
            val fromZero = listOf(0.0 to 0.7, 0.0 to 4.0, -0.05 to 13.3)
                .flatMap { (min, max) -> listOf(Triple(min, max, max), Triple(min, max, max * IOB_HEIGHT_FRACTION)) }
            val free = listOf(3.5 to 10.0, 40.0 to 260.0, 95.0 to 105.0).map { (min, max) -> Triple(min, max, max) }
            for (axisHeight in listOf(60f, 100f, 170f, 250f, 600f)) {
                for ((min, max, labelMax) in fromZero + free) {
                    val labels = niceAxisLabelValues(min, max, axisHeight, labelHeight = 20f, labelMax = labelMax)
                    val label = "axis=$min..$max height=$axisHeight labelMax=$labelMax labels=$labels"
                    if (min <= 0.0) assertWithMessage(label).that(labels.size).isAtLeast(2)
                    else assertWithMessage(label).that(labels).isNotEmpty()
                    labels.zipWithNext { a, b ->
                        assertWithMessage(label).that((b - a) / (max - min) * axisHeight).isAtLeast(20.0 - TOLERANCE)
                    }
                }
            }
        }

        @Test
        fun `before the axis has a height the labels are still round`() {
            assertThat(niceAxisLabelValues(0.0, 4.0, axisHeight = 0f, labelHeight = 0f)).containsExactly(0.0, 1.0, 2.0, 3.0, 4.0).inOrder()
        }

        @Test
        fun `an empty range has no labels`() {
            assertThat(niceAxisLabelValues(5.0, 5.0, axisHeight = 100f, labelHeight = 20f)).isEmpty()
        }
    }

    /**
     * The insulin activity overlay is never the primary series of a graph, so it has no axis of its
     * own and is mapped into the host axis' units instead. These pin that mapping: zero always
     * lands exactly on the anchor the host asked for, and the curve always stays inside the room
     * the host has — the host scale is never widened to make it fit.
     */
    @Nested
    inner class ActivityOverlayScaleTest {

        @Test
        fun `all-positive activity fills the configured fraction of the room above zero`() {
            // zeroFloorNiceRange(0, 8) -> 0..8, so the peak lands at exactly 0.8 * 100.
            val scale = activityOverlayScale(
                activityMin = 0.0, activityMax = 8.0,
                zeroLevel = 0.0, roomAbove = 100.0, roomBelow = 0.0
            )
            assertThat(scale.map(0.0)).isWithin(TOLERANCE).of(0.0)
            assertThat(scale.map(8.0)).isWithin(TOLERANCE).of(80.0)
        }

        @Test
        fun `zero is drawn at the anchor, not at the host axis floor`() {
            // BG graph case: anchor is the low mark (72), BG axis 50..200.
            val scale = activityOverlayScale(
                activityMin = 0.0, activityMax = 8.0,
                zeroLevel = 72.0, roomAbove = 128.0, roomBelow = 22.0
            )
            assertThat(scale.map(0.0)).isWithin(TOLERANCE).of(72.0)
            assertThat(scale.map(8.0)).isWithin(TOLERANCE).of(72.0 + 0.8 * 128.0)
        }

        @Test
        fun `a negative excursion that would not fit shrinks the whole curve instead of clipping`() {
            // zeroFloorNiceRange(-4, 6) -> -4..6. The positive side alone would allow 100*0.8/6,
            // but the negative side only allows 10/4 = 2.5, and the smaller limit must win.
            val scale = activityOverlayScale(
                activityMin = -4.0, activityMax = 6.0,
                zeroLevel = 0.0, roomAbove = 100.0, roomBelow = 10.0
            )
            assertThat(scale.scale).isWithin(TOLERANCE).of(2.5)
            assertThat(scale.map(-4.0)).isWithin(TOLERANCE).of(-10.0)
            assertThat(scale.map(6.0)).isWithin(TOLERANCE).of(15.0)
        }

        @Test
        fun `with no room below, the positive side keeps its full scale`() {
            // The usual case on the BG graph: while the window holds no BG below the low mark, the
            // axis floor lands on it (70/80/90/100 mg/dL, 4 or 5 mmol/L). The negative tail is
            // clipped there; the positive side must NOT be shrunk to compensate.
            val scale = activityOverlayScale(
                activityMin = -4.0, activityMax = 6.0,
                zeroLevel = 0.0, roomAbove = 100.0, roomBelow = 0.0
            )
            assertThat(scale.scale).isWithin(TOLERANCE).of(100.0 * 0.8 / 6.0)
        }

        @Test
        fun `a window holding only negative activity is scaled by its negative side`() {
            // zeroFloorNiceRange(-0.5, 0) -> -0.5..0: there is no positive side to scale from, so
            // the curve must fall back to the room below instead of collapsing to nothing.
            val scale = activityOverlayScale(
                activityMin = -0.5, activityMax = 0.0,
                zeroLevel = 0.0, roomAbove = 100.0, roomBelow = 20.0
            )
            assertThat(scale.scale).isWithin(TOLERANCE).of(40.0)
            assertThat(scale.map(-0.5)).isWithin(TOLERANCE).of(-20.0)
        }

        @Test
        fun `activity that is zero across the whole window draws nothing`() {
            // Reachable whenever there is no insulin on board at all. zeroFloorNiceRange(0, 0) is
            // 0..1 (niceScale widens a zero-width range), so without an explicit guard this would
            // get a positive scale and draw a flat line on the anchor — on the BG graph, right on
            // the low mark, where it looks like a second target line.
            val scale = activityOverlayScale(
                activityMin = 0.0, activityMax = 0.0,
                zeroLevel = 72.0, roomAbove = 128.0, roomBelow = 22.0
            )
            assertThat(scale.scale).isEqualTo(0.0)
        }

        @Test
        fun `constant non-zero activity still draws, flat at the configured fraction`() {
            // The guard above must not swallow this one: a window where activity is real but does
            // not change is a flat line at a readable height, not an empty series.
            // zeroFloorNiceRange(8, 8) -> 0..8, so the flat curve lands at exactly 0.8 * 100.
            val scale = activityOverlayScale(
                activityMin = 8.0, activityMax = 8.0,
                zeroLevel = 0.0, roomAbove = 100.0, roomBelow = 0.0
            )
            assertThat(scale.scale).isGreaterThan(0.0)
            assertThat(scale.map(8.0)).isWithin(TOLERANCE).of(80.0)
        }

        @Test
        fun `no room on the only side that has data draws nothing`() {
            val scale = activityOverlayScale(
                activityMin = -0.5, activityMax = 0.0,
                zeroLevel = 0.0, roomAbove = 100.0, roomBelow = 0.0
            )
            assertThat(scale.scale).isEqualTo(0.0)
        }

        @Test
        fun `never leaves the room it was given, across a range of data shapes`() {
            val shapes = listOf(
                0.0 to 0.02, -0.001 to 0.02, -0.01 to 0.01, -0.02 to 0.002, 0.0 to 12.0, -5.0 to 5.0
            )
            val rooms = listOf(100.0 to 0.0, 128.0 to 22.0, 128.0 to 5.0, 10.0 to 10.0, 2.5 to 0.0)
            for ((dataMin, dataMax) in shapes) {
                for ((above, below) in rooms) {
                    val scale = activityOverlayScale(dataMin, dataMax, zeroLevel = 72.0, roomAbove = above, roomBelow = below)
                    val label = "data=[$dataMin..$dataMax] room=[-$below..+$above]"
                    assertThat(scale.scale).isAtLeast(0.0)
                    assertWithMessage(label).that(scale.map(dataMax)).isAtMost(72.0 + above + TOLERANCE)
                    // The negative side is only guaranteed in frame when there is room for it —
                    // with roomBelow == 0 the tail is knowingly clipped (see the test above).
                    if (below > 0.0 && dataMin < 0.0) {
                        assertWithMessage(label).that(scale.map(dataMin)).isAtLeast(72.0 - below - TOLERANCE)
                    }
                }
            }
        }
    }

    @Nested
    inner class BgAxisTopTest {

        // mmol/L, default high mark 10 and 3 mmol/L of headroom for predictions

        @Test
        fun `a prediction a little above the real data widens the axis to it`() {
            assertThat(bgAxisTop(realMax = 9.3, predictionMax = 12.0, highMark = 10.0, headroom = 3.0)).isEqualTo(12.0)
        }

        @Test
        fun `a prediction far above the real data is capped at the headroom`() {
            // The screenshot case: readings up to 9.3, COB curve to 18. Before, the axis went to 18.
            assertThat(bgAxisTop(realMax = 9.3, predictionMax = 18.0, highMark = 10.0, headroom = 3.0)).isEqualTo(13.0)
        }

        @Test
        fun `the headroom counts from the real data when that is above the high mark`() {
            assertThat(bgAxisTop(realMax = 14.0, predictionMax = 25.0, highMark = 10.0, headroom = 3.0)).isEqualTo(17.0)
        }

        @Test
        fun `a real value is never capped`() {
            assertThat(bgAxisTop(realMax = 20.0, predictionMax = null, highMark = 10.0, headroom = 3.0)).isEqualTo(20.0)
        }

        @Test
        fun `without real data the high mark is the base`() {
            // Scrolled into the future: only prediction points in the window.
            assertThat(bgAxisTop(realMax = null, predictionMax = 11.0, highMark = 10.0, headroom = 3.0)).isEqualTo(11.0)
            assertThat(bgAxisTop(realMax = null, predictionMax = 19.0, highMark = 10.0, headroom = 3.0)).isEqualTo(13.0)
        }

        @Test
        fun `a prediction below the real data changes nothing`() {
            assertThat(bgAxisTop(realMax = 9.3, predictionMax = 5.0, highMark = 10.0, headroom = 3.0)).isEqualTo(10.0)
        }

        @Test
        fun `nothing at all gives the high mark`() {
            assertThat(bgAxisTop(realMax = null, predictionMax = null, highMark = 10.0, headroom = 3.0)).isEqualTo(10.0)
        }
    }

    private companion object {

        /** These results come out of log10/pow, so compare with a tolerance rather than exactly. */
        const val TOLERANCE = 1e-9
    }
}
