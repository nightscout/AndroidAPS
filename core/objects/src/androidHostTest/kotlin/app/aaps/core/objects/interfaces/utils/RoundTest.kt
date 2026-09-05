package app.aaps.core.objects.interfaces.utils

import app.aaps.core.interfaces.utils.Round
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.math.ceil
import kotlin.math.floor

class RoundTest {

    @Test
    fun roundToTest() {
        assertThat(Round.roundTo(0.54, 0.05)).isWithin(0.00000000000000000001).of(0.55)
        assertThat(Round.roundTo(-3.2553715764602713, 0.01)).isWithin(0.00000000000000000001).of(-3.26)
        assertThat(Round.roundTo(0.8156666666666667, 0.001)).isWithin(0.00000000000000000001).of(0.816)
        assertThat(Round.roundTo(0.235, 0.001)).isWithin(0.00000000000000000001).of(0.235)
        assertThat(Round.roundTo(0.3, 0.1)).isWithin(0.00000000000000001).of(0.3)
        assertThat(Round.roundTo(0.0016960652144170627, 0.0001)).isWithin(0.00000000000000000001).of(0.0017)
        assertThat(Round.roundTo(0.007804436682291013, 0.0001)).isWithin(0.00000000000000000001).of(0.0078)
        assertThat(Round.roundTo(0.6, 0.05)).isWithin(0.00000000000000000001).of(0.6)
        assertThat(Round.roundTo(1.49, 1.0)).isWithin(0.00000000000000000001).of(1.0)
        assertThat(Round.roundTo(0.0, 1.0)).isWithin(0.00000000000000000001).of(0.0)
    }

    @Test
    fun floorToTest() {
        // Genuine floors: a value strictly between two steps must still floor DOWN
        assertThat(Round.floorTo(0.54, 0.05)).isWithin(0.00000001).of(0.5)
        assertThat(Round.floorTo(1.59, 1.0)).isWithin(0.00000001).of(1.0)
        assertThat(Round.floorTo(0.0, 1.0)).isWithin(0.00000001).of(0.0)
        // Regression: on-grid values must not lose a whole step to IEEE-754 (x/step lands just below an integer)
        assertThat(Round.floorTo(0.15, 0.05)).isWithin(0.00000001).of(0.15)
        assertThat(Round.floorTo(0.30, 0.05)).isWithin(0.00000001).of(0.30)
        assertThat(Round.floorTo(0.95, 0.05)).isWithin(0.00000001).of(0.95)
        assertThat(Round.floorTo(0.30, 0.1)).isWithin(0.00000001).of(0.30)
        assertThat(Round.floorTo(1.20, 0.1)).isWithin(0.00000001).of(1.20)
        assertThat(Round.floorTo(0.29, 0.01)).isWithin(0.00000001).of(0.29)
    }

    @Test
    fun ceilToTest() {
        // Genuine ceilings: a value strictly between two steps must still ceil UP
        assertThat(Round.ceilTo(0.54, 0.1)).isWithin(0.00000001).of(0.6)
        assertThat(Round.ceilTo(1.49999, 1.0)).isWithin(0.00000001).of(2.0)
        assertThat(Round.ceilTo(0.0, 1.0)).isWithin(0.00000001).of(0.0)
        // Regression: on-grid values must not gain a whole step to IEEE-754 (x/step lands just above an integer)
        assertThat(Round.ceilTo(0.07, 0.01)).isWithin(0.00000001).of(0.07)
        assertThat(Round.ceilTo(0.14, 0.01)).isWithin(0.00000001).of(0.14)
        assertThat(Round.ceilTo(0.28, 0.01)).isWithin(0.00000001).of(0.28)
        assertThat(Round.ceilTo(0.56, 0.01)).isWithin(0.00000001).of(0.56)
    }

    @Test
    fun isSameTest() {
        assertThat(Round.isSame(0.54, 0.54)).isTrue()
    }

    /**
     * NaN reaching a dose rounding function means an upstream calculation already went wrong, so it
     * has to be rejected rather than rounded into a number. The throw had no test at all.
     */
    @Test
    fun `roundTo rejects NaN`() {
        assertThrows<IllegalArgumentException> { Round.roundTo(Double.NaN, 0.05) }
    }

    // region parity with the old BigDecimal implementation

    /** Exactly what Round did before: BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(step)).toDouble(). */
    private fun oldWay(n: Long, step: Double): Double =
        BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(step)).toDouble()

    /** Every step size any driver in this repo actually asks for, plus 1.0 for whole units. */
    private val realSteps = listOf(0.0001, 0.001, 0.01, 0.025, 0.05, 0.1, 0.5, 1.0)

    /**
     * The reason this can be swept rather than spot-checked: `times` claims to produce *the same
     * double* as BigDecimal did, not merely a close one. A sweep over every real step size and a wide
     * range of grid positions either holds exactly or does not.
     *
     * `isEqualTo`, deliberately - a tolerance here would hide precisely the digits this code exists to
     * get rid of.
     */
    @Test
    fun `roundTo matches the old BigDecimal result exactly`() {
        for (step in realSteps)
            for (n in -2000..2000) {
                val x = n * step
                if (x == 0.0) continue
                assertThat(Round.roundTo(x, step)).isEqualTo(oldWay(n.toLong(), step))
            }
    }

    @Test
    fun `floorTo and ceilTo match the old BigDecimal result exactly`() {
        for (step in realSteps)
            for (n in -2000..2000) {
                val x = n * step
                if (x == 0.0) continue
                // On-grid input, so both directions land on n and the comparison is about the multiply.
                assertThat(Round.floorTo(x, step)).isEqualTo(oldWay(n.toLong(), step))
                assertThat(Round.ceilTo(x, step)).isEqualTo(oldWay(n.toLong(), step))
            }
    }

    /**
     * Off-grid inputs, where floor and ceil pick different step counts. Keeps the sweep above from
     * only ever exercising the snapping branch.
     */
    @Test
    fun `off grid values still match the old result in both directions`() {
        for (step in realSteps)
            for (n in -500..500) {
                val x = n * step + step / 3.0
                if (x == 0.0) continue
                assertThat(Round.floorTo(x, step)).isEqualTo(oldWay(floor(x / step).toLong(), step))
                assertThat(Round.ceilTo(x, step)).isEqualTo(oldWay(ceil(x / step).toLong(), step))
            }
    }

    /**
     * 0.0001 is below 1e-3, so Double.toString spells it "1.0E-4". Reading the scale by looking for a
     * decimal point alone would get 1 instead of 4 and be out by a factor of a thousand. The step is
     * genuinely used, so this is a real branch, not a hypothetical one.
     */
    @Test
    fun `a step written in scientific notation keeps its scale`() {
        assertThat(0.0001.toString()).contains("E")          // the premise, in case a toString ever changes
        assertThat(Round.roundTo(0.0017, 0.0001)).isEqualTo(0.0017)
        assertThat(Round.roundTo(0.00123456, 0.0001)).isEqualTo(0.0012)
        assertThat(Round.floorTo(0.00019, 0.0001)).isEqualTo(0.0001)
    }

    /** A whole-number step has no fractional digits at all - scale 0, which is its own branch. */
    @Test
    fun `a whole number step works`() {
        assertThat(Round.roundTo(7.4, 1.0)).isEqualTo(7.0)
        assertThat(Round.roundTo(7.6, 1.0)).isEqualTo(8.0)
        assertThat(Round.floorTo(7.9, 1.0)).isEqualTo(7.0)
        assertThat(Round.ceilTo(7.1, 1.0)).isEqualTo(8.0)
    }

    /** The digits this whole function exists to avoid. */
    @Test
    fun `results carry no float noise`() {
        assertThat(Round.roundTo(0.15, 0.05)).isEqualTo(0.15)          // plain 3 * 0.05 is 0.15000000000000002
        assertThat(Round.roundTo(0.6, 0.05)).isEqualTo(0.6)            // plain 12 * 0.05 is 0.6000000000000001
        assertThat(Round.roundTo(0.3, 0.1)).isEqualTo(0.3)             // plain 3 * 0.1 is 0.30000000000000004
        assertThat(Round.roundTo(-3.2553715764602713, 0.01)).isEqualTo(-3.26)
    }

    // endregion
}
