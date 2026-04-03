package app.aaps.pump.eopatch.core.define

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class IPatchConstantTest {

    @Test
    fun `NOW_BOLUS_ID should be 0xEFFE`() {
        assertThat(IPatchConstant.NOW_BOLUS_ID).isEqualTo(0xEFFE.toShort())
    }

    @Test
    fun `EXT_BOLUS_ID should be 0xEFFF`() {
        assertThat(IPatchConstant.EXT_BOLUS_ID).isEqualTo(0xEFFF.toShort())
    }

    @Test
    fun `BOLUS_EXTENDED_DURATION_STEP should be 30`() {
        assertThat(IPatchConstant.BOLUS_EXTENDED_DURATION_STEP).isEqualTo(30.toByte())
    }

    @Test
    fun `WARRANTY_OPERATING_LIFE_MILLI should be 84 hours`() {
        assertThat(IPatchConstant.WARRANTY_OPERATING_LIFE_MILLI).isEqualTo(TimeUnit.HOURS.toMillis(84))
    }

    @Test
    fun `SERVICE_TIME_MILLI should be 12 hours`() {
        assertThat(IPatchConstant.SERVICE_TIME_MILLI).isEqualTo(TimeUnit.HOURS.toMillis(12))
    }

    @Test
    fun `BASAL_SEQ_MAX should be 1153`() {
        assertThat(IPatchConstant.BASAL_SEQ_MAX).isEqualTo(1153)
    }

    @Test
    fun `BASAL_HISTORY_SIZE_BIG should be 220`() {
        assertThat(IPatchConstant.BASAL_HISTORY_SIZE_BIG).isEqualTo(220)
    }
}
