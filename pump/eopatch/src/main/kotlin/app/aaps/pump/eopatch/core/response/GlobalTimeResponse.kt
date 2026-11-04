package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.scan.PatchSelfTestResult
import kotlin.math.abs

class GlobalTimeResponse(
    val globalTime: Int,
    val timeZoneOffset: Int
) : BaseResponse(PatchBleResultCode.SUCCESS), IPatchSelfTest {

    val globalTimeInMilli: Long get() = convertSecondToMilli(globalTime)

    override val result: PatchSelfTestResult
        get() {
            val diff = abs(System.currentTimeMillis() - globalTimeInMilli)
            return if (diff > TEST_FAIL_DIFF_MILLI) PatchSelfTestResult.TIME_SET_ERROR
            else PatchSelfTestResult.TEST_SUCCESS
        }

    companion object {
        private const val TEST_FAIL_DIFF_MILLI = 60_000L
    }
}
