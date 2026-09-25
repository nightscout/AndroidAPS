package app.aaps.pump.eopatch.core.ble

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PumpCounterTest {

    @Test
    fun `getPumpCount should convert dose to pump cycles`() {
        // 1.0U / 0.05U = 20 pump cycles
        assertThat(PumpCounter.getPumpCount(1.0f)).isEqualTo(20)
    }

    @Test
    fun `getPumpCount should return 0 for zero dose`() {
        assertThat(PumpCounter.getPumpCount(0.0f)).isEqualTo(0)
    }

    @Test
    fun `getPumpCount should return 0 for negative dose`() {
        assertThat(PumpCounter.getPumpCount(-1.0f)).isEqualTo(0)
    }

    @Test
    fun `getPumpCount should handle fractional doses`() {
        // 0.05U / 0.05U = 1 pump cycle
        assertThat(PumpCounter.getPumpCount(0.05f)).isEqualTo(1)
        // 0.10U / 0.05U = 2 pump cycles
        assertThat(PumpCounter.getPumpCount(0.10f)).isEqualTo(2)
    }

    @Test
    fun `getPumpCountShort should cap at Short MAX_VALUE`() {
        // Very large dose should be capped
        val largeDose = Short.MAX_VALUE.toFloat() * AppConstant.INSULIN_UNIT_P + 1f
        assertThat(PumpCounter.getPumpCountShort(largeDose)).isEqualTo(Short.MAX_VALUE)
    }

    @Test
    fun `getPumpCountShort should return correct short for normal dose`() {
        assertThat(PumpCounter.getPumpCountShort(1.0f)).isEqualTo(20.toShort())
    }
}
