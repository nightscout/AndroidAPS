package app.aaps.pump.eopatch.core.scan

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PatchSelfTestResultTest {

    @Test
    fun `should have exactly 3 values`() {
        assertThat(PatchSelfTestResult.entries).hasSize(3)
    }

    @Test
    fun `valueOf should return correct enum`() {
        assertThat(PatchSelfTestResult.valueOf("TEST_SUCCESS")).isEqualTo(PatchSelfTestResult.TEST_SUCCESS)
        assertThat(PatchSelfTestResult.valueOf("VOLTAGE_MIN")).isEqualTo(PatchSelfTestResult.VOLTAGE_MIN)
        assertThat(PatchSelfTestResult.valueOf("TIME_SET_ERROR")).isEqualTo(PatchSelfTestResult.TIME_SET_ERROR)
    }
}
