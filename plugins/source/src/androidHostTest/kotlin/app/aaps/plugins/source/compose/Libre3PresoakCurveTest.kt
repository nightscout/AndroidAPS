package app.aaps.plugins.source.compose

import app.aaps.plugins.source.Libre3PresoakPoint
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The scaling rule of the pre-soak curve.
 *
 */
class Libre3PresoakCurveTest {

    private val now = 1_777_216_508_000L
    private val minute = 60L * 1000L
    private val hour = 60L * minute

    private fun point(minutesAgo: Long, mgdl: Double) =
        Libre3PresoakPoint(timestampMs = now - minutesAgo * minute, mgdl = mgdl)

    @Test
    fun `no readings means nothing to draw`() {
        assertThat(Libre3PresoakCurveAxes.axisFor(emptyList(), now)).isNull()
    }

    @Test
    fun `a flat trace keeps both guide lines in view`() {
        val axis = Libre3PresoakCurveAxes.axisFor(
            listOf(point(30, 120.0), point(20, 120.0), point(10, 120.0)),
            now,
        )!!

        assertThat(axis.lowMgdl).isEqualTo(Libre3PresoakCurveAxes.LOW_GUIDE_MGDL)
        assertThat(axis.highMgdl).isEqualTo(Libre3PresoakCurveAxes.HIGH_GUIDE_MGDL)
        assertThat(Libre3PresoakCurveAxes.yFraction(axis, 120.0)).isWithin(0.01f).of(0.45f)
    }

    @Test
    fun `readings outside the guides widen the range`() {
        val axis = Libre3PresoakCurveAxes.axisFor(listOf(point(10, 42.0), point(5, 260.0)), now)!!

        assertThat(axis.lowMgdl).isEqualTo(42.0)
        assertThat(axis.highMgdl).isEqualTo(260.0)
        assertThat(Libre3PresoakCurveAxes.yFraction(axis, 42.0)).isEqualTo(0f)
        assertThat(Libre3PresoakCurveAxes.yFraction(axis, 260.0)).isEqualTo(1f)
    }

    @Test
    fun `a short soak still gets the smallest window`() {
        val axis = Libre3PresoakCurveAxes.axisFor(listOf(point(2, 100.0)), now)!!

        assertThat(axis.endMs).isEqualTo(now)
        assertThat(axis.endMs - axis.startMs).isEqualTo(Libre3PresoakCurveAxes.MIN_WINDOW_MS)
        assertThat(Libre3PresoakCurveAxes.xFraction(axis, now - 2 * minute)).isWithin(0.01f).of(0.967f)
    }

    @Test
    fun `a long soak stretches the window to the oldest reading`() {
        val axis = Libre3PresoakCurveAxes.axisFor(listOf(point(360, 100.0), point(0, 110.0)), now)!!

        assertThat(axis.startMs).isEqualTo(now - 6 * hour)
        assertThat(Libre3PresoakCurveAxes.xFraction(axis, now - 6 * hour)).isEqualTo(0f)
        assertThat(Libre3PresoakCurveAxes.xFraction(axis, now - 3 * hour)).isWithin(0.01f).of(0.5f)
        assertThat(Libre3PresoakCurveAxes.xFraction(axis, now)).isEqualTo(1f)
    }

    @Test
    fun `a reading newer than the clock pushes the right edge out instead of falling off`() {
        val axis = Libre3PresoakCurveAxes.axisFor(listOf(point(-5, 100.0)), now)!!

        assertThat(axis.endMs).isEqualTo(now + 5 * minute)
        assertThat(Libre3PresoakCurveAxes.xFraction(axis, now + 5 * minute)).isEqualTo(1f)
    }

    @Test
    fun `a value off the box is pinned to the nearest edge`() {
        val axis = Libre3PresoakCurveAxes.axisFor(listOf(point(10, 120.0)), now)!!

        assertThat(Libre3PresoakCurveAxes.yFraction(axis, 20.0)).isEqualTo(0f)
        assertThat(Libre3PresoakCurveAxes.yFraction(axis, 500.0)).isEqualTo(1f)
        assertThat(Libre3PresoakCurveAxes.xFraction(axis, now - 10 * hour)).isEqualTo(0f)
    }
}
