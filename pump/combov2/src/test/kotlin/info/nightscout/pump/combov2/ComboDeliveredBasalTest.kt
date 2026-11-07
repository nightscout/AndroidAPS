package info.nightscout.pump.combov2

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.defs.fillFor
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * #2454: AAPS keeps what the pump runs as the running basal. The Combo takes 0.01 U/h steps below 1 U/h, 0.05 U/h
 * steps up to 10 U/h and 0.1 U/h steps above, so the profile it is given comes back rounded.
 */
class ComboDeliveredBasalTest {

    private fun profileOf(rate: (hour: Int) -> Double): Profile = mock<Profile>().also { profile ->
        whenever(profile.getBasalTimeFromMidnight(any())).thenAnswer { rate(it.getArgument<Int>(0) / 3600) }
    }

    private fun delivered(rate: (hour: Int) -> Double) = profileOf(rate).toComboCtlBasalProfile().toHourlyProfileValues()

    @Test
    fun `each hour is reported from midnight`() {
        val values = delivered { 0.5 }

        assertThat(values.map { it.timeAsSeconds }).containsExactlyElementsIn((0..23).map { it * 3600 }).inOrder()
    }

    @Test
    fun `rates come back in the steps of the Combo`() {
        val rates = listOf(0.558, 0.84, 1.23, 1.27, 10.04, 10.06)
        val values = delivered { hour -> rates[hour % rates.size] }

        assertThat(values.take(6).map { it.value }).containsExactly(0.56, 0.84, 1.25, 1.25, 10.0, 10.1).inOrder()
    }

    /** Below 0.05 U/h the Combo takes only 0 or 0.05: 0.01 - 0.024 U/h became no basal at all. The minimum refuses them. */
    @Test
    fun `no rate from the Combo minimum up becomes zero`() {
        val minimum = PumpDescription().fillFor(PumpType.ACCU_CHEK_COMBO).basalMinimumRate
        assertThat(minimum).isEqualTo(0.05)

        for (thousandths in 50..300) {
            val rate = thousandths / 1000.0
            assertWithMessage("rate $rate").that(delivered { rate }.first().value).isAtLeast(0.05)
        }
    }

    @Test
    fun `rates on a step come back unchanged`() {
        val values = delivered { hour -> 0.05 + hour * 0.01 }

        values.forEachIndexed { hour, value -> assertThat(value.value).isWithin(1e-9).of(0.05 + hour * 0.01) }
    }
}
