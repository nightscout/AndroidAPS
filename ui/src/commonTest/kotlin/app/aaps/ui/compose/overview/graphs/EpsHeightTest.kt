package app.aaps.ui.compose.overview.graphs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Height of the profile icon on the BG graph, see [epsY].
 *
 * The height tells the profile percentage, so it is measured on the axis that is drawn. These pin
 * the two ways the icon used to leave the graph: a strong profile drawn above the top edge, and a
 * height taken from the whole loaded range while the axis shows a zoomed window.
 */
class EpsHeightTest {

    private val axisMin = 40.0
    private val axisMax = 240.0
    private val span = axisMax - axisMin

    @Test fun `a normal profile sits at three quarters of the axis`() {
        assertEquals(axisMin + 0.75 * span, epsY(100, axisMin, axisMax), 0.001)
    }

    @Test fun `a weaker profile sits lower and a stronger one higher`() {
        assertTrue(epsY(80, axisMin, axisMax) < epsY(100, axisMin, axisMax))
        assertTrue(epsY(120, axisMin, axisMax) > epsY(100, axisMin, axisMax))
    }

    @Test fun `the height is proportional to the percentage`() {
        assertEquals(axisMin + 0.5 * 0.75 * span, epsY(50, axisMin, axisMax), 0.001)
        assertEquals(axisMin + 1.2 * 0.75 * span, epsY(120, axisMin, axisMax), 0.001)
    }

    @Test fun `a zero percent profile sits on the axis floor and not below it`() {
        assertEquals(axisMin, epsY(0, axisMin, axisMax), 0.001)
    }

    /** The case of the report: 135% used to land above the top of the range and be clipped away. */
    @Test fun `a strong profile stays inside the graph`() {
        val y = epsY(135, axisMin, axisMax)
        assertEquals(axisMin + 0.95 * span, y, 0.001)
        assertTrue(y < axisMax)
    }

    @Test fun `an extreme profile is pinned at the same height`() {
        assertEquals(epsY(135, axisMin, axisMax), epsY(500, axisMin, axisMax), 0.001)
    }

    /**
     * Zooming changes the axis, so the icon keeps the same place on the screen. The old code took
     * the bounds from the whole loaded range, and the icon then wandered out of the window.
     */
    @Test fun `the height follows the axis of the window`() {
        val zoomedMin = 60.0
        val zoomedMax = 140.0
        val y = epsY(100, zoomedMin, zoomedMax)
        assertEquals(zoomedMin + 0.75 * (zoomedMax - zoomedMin), y, 0.001)
        assertTrue(y in zoomedMin..zoomedMax)
    }

    @Test fun `every percentage of a real profile stays inside any axis`() {
        for (percentage in 0..500 step 5) {
            for ((min, max) in listOf(40.0 to 240.0, 60.0 to 140.0, 2.0 to 13.5)) {
                val y = epsY(percentage, min, max)
                assertTrue(y in min..max, "percentage $percentage on axis $min..$max gave $y")
            }
        }
    }
}
