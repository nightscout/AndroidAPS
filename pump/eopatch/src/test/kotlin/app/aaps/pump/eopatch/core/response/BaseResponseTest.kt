package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class BaseResponseTest {

    private class TestResponse(result: PatchBleResultCode = PatchBleResultCode.SUCCESS) : BaseResponse(result)

    @Test
    fun `default constructor should set SUCCESS`() {
        val response = TestResponse()
        assertThat(response.isSuccess).isTrue()
        assertThat(response.resultCode).isEqualTo(PatchBleResultCode.SUCCESS)
    }

    @Test
    fun `constructor with error code should not be success`() {
        val response = TestResponse(PatchBleResultCode.UNKNOWN_ERROR)
        assertThat(response.isSuccess).isFalse()
    }

    @Test
    fun `timestamp should be set to current time`() {
        val before = System.currentTimeMillis()
        val response = TestResponse()
        val after = System.currentTimeMillis()
        assertThat(response.timestamp).isIn(before..after)
    }

    @Test
    fun `convertSecondToMilli should convert correctly`() {
        val response = TestResponse()
        assertThat(response.convertSecondToMilli(60)).isEqualTo(TimeUnit.SECONDS.toMillis(60))
        assertThat(response.convertSecondToMilli(0)).isEqualTo(0)
    }

    @Test
    fun `toString should contain resultCode`() {
        val response = TestResponse(PatchBleResultCode.BOLUS_OVERRUN)
        assertThat(response.toString()).contains("BOLUS_OVERRUN")
    }
}
