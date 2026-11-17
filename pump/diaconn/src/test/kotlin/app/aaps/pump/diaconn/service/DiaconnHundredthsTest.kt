package app.aaps.pump.diaconn.service

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The amounts sent to the pump, in hundredths of a unit. */
class DiaconnHundredthsTest {

    // 0.29 * 100 is 28.99999...: a cut sent a bolus of 0.29 U as 0.28 U, and a rate of 1.15 U/h as 1.14 U/h.
    @Test
    fun anAmountIsRoundedNotCut() {
        assertThat(DiaconnG8Service.hundredths(0.29)).isEqualTo(29)
        assertThat(DiaconnG8Service.hundredths(1.15)).isEqualTo(115)
        assertThat(DiaconnG8Service.hundredths(0.57)).isEqualTo(57)
    }

    @Test
    fun aWholeAmountStaysTheSame() {
        assertThat(DiaconnG8Service.hundredths(0.0)).isEqualTo(0)
        assertThat(DiaconnG8Service.hundredths(5.0)).isEqualTo(500)
    }
}
