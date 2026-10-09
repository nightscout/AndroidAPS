package app.aaps.ui.compose.overview.graphs

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Regression coverage for the "nice numbers" axis-scaling math (Heckbert's algorithm and its
 * pivot/zero-floor variants), extracted during the Step 4 graph-scale refactor. Expected values
 * verified by direct calculation against the algorithm, not guessed — several were hand-verified
 * during that work and are pinned here so a future change can't silently re-break them.
 */
internal class GraphUtilsTest {

    @Nested
    inner class NiceScaleTest {

        @ParameterizedTest(name = "max just above {0} rounds to a clean ({1}, {2})")
        @CsvSource(
            "195.0, 200.0, 50.0",
            "210.0, 250.0, 50.0", // the 2.5-tier fix: without it this jumps straight to 300/100
            "250.0, 250.0, 50.0",
            "260.0, 300.0, 100.0",
            "310.0, 400.0, 100.0",
        )
        fun `BG max rounds to the expected clean ceiling and step`(input: Double, expectedMax: Double, expectedStep: Double) {
            val scale = niceScale(0.0, input)
            assertThat(scale.min).isEqualTo(0.0)
            assertThat(scale.max).isEqualTo(expectedMax)
            assertThat(scale.step).isEqualTo(expectedStep)
        }

        @Test
        fun `never clips the real data range`() {
            val scale = niceScale(12.39, 57.39)
            assertThat(scale.min).isEqualTo(10.0)
            assertThat(scale.max).isEqualTo(60.0)
            assertThat(scale.step).isEqualTo(10.0)
        }

        @Test
        fun `degenerate range (min equals max) still produces a usable non-empty scale`() {
            val scale = niceScale(5.0, 5.0)
            assertThat(scale.max).isGreaterThan(scale.min)
            assertThat(scale.step).isGreaterThan(0.0)
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
            assertThat(niceUp(82.0)).isEqualTo(100.0)
            assertThat(niceUp(82.0)).isAtLeast(82.0)
        }
    }

    @Nested
    inner class NiceNegativeSliverTest {

        @Test
        fun `non-negative values return zero`() {
            assertThat(niceNegativeSliver(0.0)).isEqualTo(0.0)
            assertThat(niceNegativeSliver(5.0)).isEqualTo(0.0)
        }

        @Test
        fun `rounds further negative, never toward zero`() {
            assertThat(niceNegativeSliver(-0.3)).isEqualTo(-0.5)
            assertThat(niceNegativeSliver(-0.07)).isEqualTo(-0.1)
        }

        @Test
        fun `a value already nice is left unchanged`() {
            // 0.05 is itself on the 1/2/2.5/5/10 ladder, so it must not get bumped to 0.1
            assertThat(niceNegativeSliver(-0.05)).isEqualTo(-0.05)
        }
    }

    @Nested
    inner class NiceScaleAroundPivotTest {

        @Test
        fun `pivot always sits exactly at the midpoint regardless of input asymmetry`() {
            val scale = niceScaleAroundPivot(min = 60.0, max = 108.0, pivot = 100.0)
            assertThat(scale.min).isEqualTo(40.0)
            assertThat(scale.max).isEqualTo(160.0)
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
            assertThat(scale.min).isEqualTo(-6.0)
            assertThat(scale.max).isEqualTo(6.0)
        }

        @Test
        fun `SENS sitting flat on its pivot snaps to a fixed 95-100-105 scale, not a tight near-zero one`() {
            // Regression for the reported bug: a constant SENS at 100% used to produce something
            // like 99/99.5/100/100.5/101 instead of a clean 95%/100%/105%.
            val scale = niceScaleAroundPivot(min = 100.0, max = 100.0, pivot = 100.0, minDeviation = 5.0)
            assertThat(scale.min).isEqualTo(95.0)
            assertThat(scale.max).isEqualTo(105.0)
            assertThat(scale.step).isEqualTo(5.0)
        }

        @Test
        fun `deviation above the minDeviation floor ignores the floor and nice-ifies normally`() {
            val scale = niceScaleAroundPivot(min = 40.0, max = 160.0, pivot = 100.0, minDeviation = 5.0)
            assertThat(scale.min).isEqualTo(0.0)
            assertThat(scale.max).isEqualTo(200.0)
        }

        @Test
        fun `requesting 3 ticks always produces exactly 3 (pivot guaranteed to be one of them)`() {
            // The SENS_PIVOT_TICK_COUNT fix: side-steps Vico's step-thinning entirely by never
            // requesting more ticks than always fit.
            val scale = niceScaleAroundPivot(min = 40.0, max = 160.0, pivot = 100.0, maxTickCount = 3)
            val tickCount = Math.round((scale.max - scale.min) / scale.step) + 1
            assertThat(tickCount).isEqualTo(3L)
            assertThat(scale.min + (scale.max - scale.min) / 2.0).isEqualTo(100.0)
        }
    }

    @Nested
    inner class ZeroFloorNiceRangeTest {

        @Test
        fun `all-positive data floors at exactly zero`() {
            val scale = zeroFloorNiceRange(dataMin = 5.0, dataMax = 82.0)
            assertThat(scale.min).isEqualTo(0.0)
            assertThat(scale.max).isEqualTo(100.0)
        }

        @Test
        fun `tiny negative excursion relative to a large positive side gets its own independent sliver`() {
            // ratio (8.0 / 0.05 = 160) is far above the default disparityRatio of 10 — the negative
            // sliver must stay tight (its own nice magnitude), not stretched to match the positive step.
            val scale = zeroFloorNiceRange(dataMin = -0.05, dataMax = 8.0)
            assertThat(scale.min).isEqualTo(-0.05)
            assertThat(scale.max).isEqualTo(10.0)
        }

        @Test
        fun `comparable-magnitude negative and positive share one unified nice scale`() {
            val scale = zeroFloorNiceRange(dataMin = -4.0, dataMax = 6.0)
            assertThat(scale.min).isEqualTo(-4.0)
            assertThat(scale.max).isEqualTo(6.0)
            assertThat(scale.step).isEqualTo(2.0)
        }

        @Test
        fun `right at the disparity ratio boundary takes the independent-sliver branch`() {
            // ratio == disparityRatio exactly (10.0 / 1.0 = 10.0) must take the ">=" sliver branch,
            // not silently fall through to the unified one.
            val scale = zeroFloorNiceRange(dataMin = -1.0, dataMax = 10.0)
            assertThat(scale.min).isEqualTo(-1.0)
            assertThat(scale.max).isEqualTo(10.0)
            assertThat(scale.step).isEqualTo(2.0)
        }

        @Test
        fun `never clips real data even when the negative side is larger than the positive side`() {
            val scale = zeroFloorNiceRange(dataMin = -12.0, dataMax = 3.0)
            assertThat(scale.min).isAtMost(-12.0)
            assertThat(scale.max).isAtLeast(3.0)
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
            // Reachable on the BG graph whenever the low mark is an exact multiple of the BG axis
            // step and the window holds no BG below it (80/90/100 mg/dL, 4 or 5 mmol/L). The
            // negative tail is clipped there; the positive side must NOT be shrunk to compensate.
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

    private companion object {

        /** These results come out of log10/pow, so compare with a tolerance rather than exactly. */
        const val TOLERANCE = 1e-9
    }
}
