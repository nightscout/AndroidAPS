package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.scan.PatchSelfTestResult

class BatteryVoltageLevelPairingResponse(
    private val voltage: Int,
    private val percent: Int,
    val error: Int
) : BaseResponse(), IPatchSelfTest {

    override val result: PatchSelfTestResult
        get() = if (error == 1) PatchSelfTestResult.VOLTAGE_MIN else PatchSelfTestResult.TEST_SUCCESS
}
