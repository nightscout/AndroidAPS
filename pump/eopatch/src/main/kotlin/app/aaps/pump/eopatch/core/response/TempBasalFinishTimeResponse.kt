package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode

class TempBasalFinishTimeResponse(
    val tempBasalFinishTime: Int,
    resultCode: PatchBleResultCode
) : BaseResponse(resultCode)
