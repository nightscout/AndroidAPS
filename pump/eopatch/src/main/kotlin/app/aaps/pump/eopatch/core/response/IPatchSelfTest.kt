package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.scan.PatchSelfTestResult

interface IPatchSelfTest {
    val result: PatchSelfTestResult
}
