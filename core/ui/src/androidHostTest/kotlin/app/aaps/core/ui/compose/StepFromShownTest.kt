package app.aaps.core.ui.compose

import app.aaps.core.data.format.NumberFormat
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The stepper moves the value as the field shows it by one step. Autotune writes basal rates with three
 * decimals, and stepping from the stored value moved the shown hundredths by two, or not at all (#5401).
 */
class StepFromShownTest {

    private val range = 0.05..10.0

    private fun up(value: Double) = stepFromShown(value, 1, 0.01, NumberFormat.DECIMAL_2, range)
    private fun down(value: Double) = stepFromShown(value, -1, 0.01, NumberFormat.DECIMAL_2, range)

    @Test
    fun aValueOnTheGridMovesByOneStep() {
        assertThat(up(1.21)).isEqualTo(1.22)
        assertThat(down(1.21)).isEqualTo(1.20)
    }

    /** 1.125 is shown as 1.12. It stepped to 1.14 up and stayed 1.12 down. */
    @Test
    fun aHiddenFiveInTheThirdDecimalStepsFromTheShownValue() {
        assertThat(up(1.125)).isEqualTo(1.13)
        assertThat(down(1.125)).isEqualTo(1.11)
    }

    /** 1.115 is a little below 1.115 in binary and shown as 1.11. It stepped to 1.13 up. */
    @Test
    fun aValueJustBelowTheMiddleStepsFromTheShownValue() {
        assertThat(up(1.115)).isEqualTo(1.12)
        assertThat(down(1.115)).isEqualTo(1.10)
    }

    @Test
    fun theStepStaysInsideTheRange() {
        assertThat(down(0.05)).isEqualTo(0.05)
        assertThat(up(10.0)).isEqualTo(10.0)
    }

    /** A step coarser than the shown digits still lands on its own grid. */
    @Test
    fun aCoarserStepLandsOnItsGrid() {
        assertThat(stepFromShown(1.12, 1, 0.05, NumberFormat.DECIMAL_2, range)).isEqualTo(1.15)
        assertThat(stepFromShown(98.4, 1, 1.0, NumberFormat.INTEGER, 0.0..400.0)).isEqualTo(99.0)
    }
}
