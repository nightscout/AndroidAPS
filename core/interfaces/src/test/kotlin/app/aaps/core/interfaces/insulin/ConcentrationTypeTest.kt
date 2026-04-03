package app.aaps.core.interfaces.insulin

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ConcentrationTypeTest {

    @Test
    fun `fromDouble returns correct type for known values`() {
        assertThat(ConcentrationType.fromDouble(0.1)).isEqualTo(ConcentrationType.U10)
        assertThat(ConcentrationType.fromDouble(0.4)).isEqualTo(ConcentrationType.U40)
        assertThat(ConcentrationType.fromDouble(0.5)).isEqualTo(ConcentrationType.U50)
        assertThat(ConcentrationType.fromDouble(1.0)).isEqualTo(ConcentrationType.U100)
        assertThat(ConcentrationType.fromDouble(2.0)).isEqualTo(ConcentrationType.U200)
        assertThat(ConcentrationType.fromDouble(3.0)).isEqualTo(ConcentrationType.U300)
        assertThat(ConcentrationType.fromDouble(5.0)).isEqualTo(ConcentrationType.U500)
    }

    @Test
    fun `fromDouble returns UNKNOWN for unrecognized value`() {
        assertThat(ConcentrationType.fromDouble(0.0)).isEqualTo(ConcentrationType.UNKNOWN)
        assertThat(ConcentrationType.fromDouble(1.5)).isEqualTo(ConcentrationType.UNKNOWN)
        assertThat(ConcentrationType.fromDouble(7.0)).isEqualTo(ConcentrationType.UNKNOWN)
    }

    @Test
    fun `fromInt returns correct type for known values`() {
        assertThat(ConcentrationType.fromInt(10)).isEqualTo(ConcentrationType.U10)
        assertThat(ConcentrationType.fromInt(40)).isEqualTo(ConcentrationType.U40)
        assertThat(ConcentrationType.fromInt(50)).isEqualTo(ConcentrationType.U50)
        assertThat(ConcentrationType.fromInt(100)).isEqualTo(ConcentrationType.U100)
        assertThat(ConcentrationType.fromInt(200)).isEqualTo(ConcentrationType.U200)
        assertThat(ConcentrationType.fromInt(300)).isEqualTo(ConcentrationType.U300)
        assertThat(ConcentrationType.fromInt(500)).isEqualTo(ConcentrationType.U500)
    }

    @Test
    fun `fromInt returns UNKNOWN for unrecognized value`() {
        assertThat(ConcentrationType.fromInt(0)).isEqualTo(ConcentrationType.UNKNOWN)
        assertThat(ConcentrationType.fromInt(150)).isEqualTo(ConcentrationType.UNKNOWN)
        assertThat(ConcentrationType.fromInt(999)).isEqualTo(ConcentrationType.UNKNOWN)
    }

    @Test
    fun `value property matches expected concentration multiplier`() {
        assertThat(ConcentrationType.U10.value).isEqualTo(0.1)
        assertThat(ConcentrationType.U40.value).isEqualTo(0.4)
        assertThat(ConcentrationType.U50.value).isEqualTo(0.5)
        assertThat(ConcentrationType.U100.value).isEqualTo(1.0)
        assertThat(ConcentrationType.U200.value).isEqualTo(2.0)
        assertThat(ConcentrationType.U300.value).isEqualTo(3.0)
        assertThat(ConcentrationType.U500.value).isEqualTo(5.0)
        assertThat(ConcentrationType.UNKNOWN.value).isEqualTo(-1.0)
    }
}
