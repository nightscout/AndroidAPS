package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode

open class BolusResponse(
    resultCode: PatchBleResultCode = PatchBleResultCode.SUCCESS,
    val id: Int = 0
) : BaseResponse(resultCode)
