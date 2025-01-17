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
        assertThat(Round.floorTo(0.54, 0.05)).isWithin(0.00000001).of(0.5)
        assertThat(Round.floorTo(1.59, 1.0)).isWithin(0.00000001).of(1.0)
        assertThat(Round.floorTo(0.0, 1.0)).isWithin(0.00000001).of(0.0)
    }

    @Test
    fun ceilToTest() {
        assertThat(Round.ceilTo(0.54, 0.1)).isWithin(0.00000001).of(0.6)
        assertThat(Round.ceilTo(1.49999, 1.0)).isWithin(0.00000001).of(2.0)
        assertThat(Round.ceilTo(0.0, 1.0)).isWithin(0.00000001).of(0.0)
    }

    @Test
    fun isSameTest() {
        assertThat(Round.isSame(0.54, 0.54)).isTrue()
    }
}
