package app.aaps.core.interfaces.pump

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PumpInsulinTest {

    @Test
    fun `iU with U100 returns same value`() {
        val pi = PumpInsulin(3.0)
        assertThat(pi.iU(1.0)).isEqualTo(3.0)
    }

    @Test
    fun `iU with U200 doubles value`() {
        val pi = PumpInsulin(3.0)
        assertThat(pi.iU(2.0)).isEqualTo(6.0)
    }

    @Test
    fun `iU with U50 halves value`() {
        val pi = PumpInsulin(4.0)
        assertThat(pi.iU(0.5)).isEqualTo(2.0)
    }

    @Test
    fun `iU with U500 multiplies by 5`() {
        val pi = PumpInsulin(1.0)
        assertThat(pi.iU(5.0)).isEqualTo(5.0)
    }

    @Test
    fun `iU with U10 multiplies by 0_1`() {
        val pi = PumpInsulin(10.0)
        assertThat(pi.iU(0.1)).isWithin(0.001).of(1.0)
    }

    @Test
    fun `cU returns raw value`() {
        val pi = PumpInsulin(3.5)
        assertThat(pi.cU).isEqualTo(3.5)
    }

    @Test
    fun `equals compares cU values`() {
        assertThat(PumpInsulin(3.0)).isEqualTo(PumpInsulin(3.0))
        assertThat(PumpInsulin(3.0)).isNotEqualTo(PumpInsulin(3.1))
    }

    @Test
    fun `hashCode is consistent`() {
        assertThat(PumpInsulin(3.0).hashCode()).isEqualTo(PumpInsulin(3.0).hashCode())
    }

    @Test
    fun `toString formats correctly`() {
        assertThat(PumpInsulin(3.5).toString()).isEqualTo("PumpInsulin(3.5)")
    }
}
