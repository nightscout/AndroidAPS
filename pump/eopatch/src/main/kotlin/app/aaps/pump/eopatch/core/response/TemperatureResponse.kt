package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.scan.PatchSelfTestResult

class TemperatureResponse(val temperature: Int) : BaseResponse(), IPatchSelfTest {

    override val result: PatchSelfTestResult get() = PatchSelfTestResult.TEST_SUCCESS
}
