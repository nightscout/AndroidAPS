package app.aaps.core.interfaces.pump

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PumpRateTest {

    @Test
    fun `iU absolute with U100 returns same value`() {
        val pr = PumpRate(2.0)
        assertThat(pr.iU(1.0, isAbsolute = true)).isEqualTo(2.0)
    }

    @Test
    fun `iU absolute with U200 doubles value`() {
        val pr = PumpRate(2.0)
        assertThat(pr.iU(2.0, isAbsolute = true)).isEqualTo(4.0)
    }

    @Test
    fun `iU absolute with U50 halves value`() {
        val pr = PumpRate(4.0)
        assertThat(pr.iU(0.5, isAbsolute = true)).isEqualTo(2.0)
    }

    @Test
    fun `iU percent with U200 returns unchanged value`() {
        val pr = PumpRate(150.0)
        assertThat(pr.iU(2.0, isAbsolute = false)).isEqualTo(150.0)
    }

    @Test
    fun `iU percent with U50 returns unchanged value`() {
        val pr = PumpRate(200.0)
        assertThat(pr.iU(0.5, isAbsolute = false)).isEqualTo(200.0)
    }

    @Test
    fun `iU percent with U100 returns unchanged value`() {
        val pr = PumpRate(100.0)
        assertThat(pr.iU(1.0, isAbsolute = false)).isEqualTo(100.0)
    }

    @Test
    fun `cU returns raw value`() {
        val pr = PumpRate(1.5)
        assertThat(pr.cU).isEqualTo(1.5)
    }

    @Test
    fun `equals compares cU values`() {
        assertThat(PumpRate(2.0)).isEqualTo(PumpRate(2.0))
        assertThat(PumpRate(2.0)).isNotEqualTo(PumpRate(2.1))
    }

    @Test
    fun `hashCode is consistent`() {
        assertThat(PumpRate(2.0).hashCode()).isEqualTo(PumpRate(2.0).hashCode())
    }

    @Test
    fun `toString formats correctly`() {
        assertThat(PumpRate(1.5).toString()).isEqualTo("PumpRate(1.5)")
    }
}
