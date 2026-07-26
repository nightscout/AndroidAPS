package app.aaps.core.objects.interfaces.utils

import app.aaps.core.interfaces.utils.Round
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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
}
