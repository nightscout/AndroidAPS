package app.aaps.ui.compose.overview.graphs

import com.google.common.truth.Truth.assertThat
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
}
