package app.aaps.core.interfaces.pump

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.defs.fillFor
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** The default [Pump.bolusStepSize]: the description wins over the pump type (#946). */
class PumpBolusStepSizeTest {

    private fun pumpWith(description: PumpDescription): Pump {
        val pump = mock<Pump>()
        whenever(pump.pumpDescription).thenReturn(description)
        whenever(pump.bolusStepSize(any())).thenCallRealMethod()
        return pump
    }

    @Test
    fun `without a plugin override the step is the pump type step`() {
        val pump = pumpWith(PumpDescription().fillFor(PumpType.DANA_RS))

        assertThat(pump.bolusStepSize(1.55)).isEqualTo(0.05)
    }

    @Test
    fun `the step the plugin set in the description wins over the pump type`() {
        // A Dana RS that reports a 0.1 U step.
        val pump = pumpWith(PumpDescription().fillFor(PumpType.DANA_RS).also { it.bolusStep = 0.1 })

        assertThat(pump.bolusStepSize(1.55)).isEqualTo(0.1)
    }

    @Test
    fun `a pump type with steps per amount uses the step for that amount`() {
        val pump = pumpWith(PumpDescription().fillFor(PumpType.ACCU_CHEK_INSIGHT_VIRTUAL))

        assertThat(pump.bolusStepSize(1.0)).isEqualTo(0.05)
        assertThat(pump.bolusStepSize(3.0)).isEqualTo(0.1)
        assertThat(pump.bolusStepSize(7.0)).isEqualTo(0.2)
        assertThat(pump.bolusStepSize(12.0)).isEqualTo(0.5)
    }
}
