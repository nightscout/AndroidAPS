package app.aaps.core.interfaces.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins [Round] on every target, because these values become insulin.
 *
 * The JVM side has its own `RoundTest`, which checks the arithmetic against `BigDecimal` as an
 * independent oracle. That oracle cannot leave the JVM, so this test states the behaviour callers
 * actually depend on instead: a value already on the pump's step grid must come back unchanged, and
 * the direction of each function must never reverse.
 */
class RoundGridTest {

    /** Steps real pumps use. */
    private val steps = listOf(0.01, 0.05, 0.1, 0.5)

    @Test
    fun `a value already on the grid survives floorTo unchanged`() {
        // The under-dose case: 0.15 / 0.05 is 2.9999999999999996, so a naive floor drops a whole
        // step and the pump is asked for 0.10 when the caller said 0.15.
        assertEquals(0.15, Round.floorTo(0.15, 0.05))
        assertEquals(0.3, Round.floorTo(0.3, 0.05))
        assertEquals(0.07, Round.floorTo(0.07, 0.01))
        assertEquals(1.0, Round.floorTo(1.0, 0.1))
    }

    @Test
    fun `a value already on the grid survives ceilTo unchanged`() {
        // The mirror over-dose case: 0.07 / 0.01 is 7.000000000000001, so a naive ceil adds a step.
        assertEquals(0.07, Round.ceilTo(0.07, 0.01))
        assertEquals(0.15, Round.ceilTo(0.15, 0.05))
        assertEquals(1.0, Round.ceilTo(1.0, 0.1))
    }

    @Test
    fun `floorTo never returns more than it was given`() {
        for (step in steps) {
            var x = 0.0
            while (x < 10.0) {
                assertTrue(Round.floorTo(x, step) <= x + 1e-12, "floorTo($x, $step) exceeded x")
                x += 0.013
            }
        }
    }

    @Test
    fun `ceilTo never returns less than it was given`() {
        for (step in steps) {
            var x = 0.0
            while (x < 10.0) {
                assertTrue(Round.ceilTo(x, step) >= x - 1e-12, "ceilTo($x, $step) was below x")
                x += 0.013
            }
        }
    }

    @Test
    fun `a value between two grid points goes to the right neighbour`() {
        assertEquals(0.1, Round.floorTo(0.14, 0.05))
        assertEquals(0.15, Round.ceilTo(0.14, 0.05))
        assertEquals(0.07, Round.floorTo(0.079, 0.01))
        assertEquals(0.08, Round.ceilTo(0.071, 0.01))
    }

    @Test
    fun `zero stays zero for every step`() {
        for (step in steps) {
            assertEquals(0.0, Round.roundTo(0.0, step))
            assertEquals(0.0, Round.floorTo(0.0, step))
            assertEquals(0.0, Round.ceilTo(0.0, step))
        }
    }

    @Test
    fun `roundTo goes to the nearer grid point`() {
        assertEquals(0.13, Round.roundTo(0.126, 0.01))
        assertEquals(0.12, Round.roundTo(0.124, 0.01))
        assertEquals(0.15, Round.roundTo(0.15, 0.05))
    }

    @Test
    fun `NaN is refused rather than turned into a dose`() {
        assertFailsWith<IllegalArgumentException> { Round.roundTo(Double.NaN, 0.05) }
    }

    @Test
    fun `isSame ignores a difference far below any pump step`() {
        assertTrue(Round.isSame(0.15, 0.15000000000000002))
        assertTrue(!Round.isSame(0.15, 0.16))
    }
}
