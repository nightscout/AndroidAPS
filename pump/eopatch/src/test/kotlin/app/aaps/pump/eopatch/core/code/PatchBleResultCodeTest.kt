package app.aaps.pump.eopatch.core.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PatchBleResultCodeTest {

    @Test
    fun `SUCCESS isSuccess should return true`() {
        assertThat(PatchBleResultCode.SUCCESS.isSuccess).isTrue()
    }

    @Test
    fun `non-SUCCESS isSuccess should return false`() {
        assertThat(PatchBleResultCode.UNKNOWN_ERROR.isSuccess).isFalse()
        assertThat(PatchBleResultCode.BOLUS_OTHER_RUNNING.isSuccess).isFalse()
        assertThat(PatchBleResultCode.BASAL_SCHEDULE_SET_NOT_SORTED.isSuccess).isFalse()
    }

    @Test
    fun `should have all expected values`() {
        val values = PatchBleResultCode.entries
        assertThat(values).hasSize(16)
        assertThat(values).contains(PatchBleResultCode.SUCCESS)
        assertThat(values).contains(PatchBleResultCode.TEMP_BASAL_NOT_EXIST)
        assertThat(values).contains(PatchBleResultCode.TEMP_BASAL_NOT_FINISH)
        assertThat(values).contains(PatchBleResultCode.UNKNOWN_ERROR)
    }

    @Test
    fun `valueOf should work`() {
        assertThat(PatchBleResultCode.valueOf("SUCCESS")).isEqualTo(PatchBleResultCode.SUCCESS)
        assertThat(PatchBleResultCode.valueOf("UNKNOWN_ERROR")).isEqualTo(PatchBleResultCode.UNKNOWN_ERROR)
    }
}
