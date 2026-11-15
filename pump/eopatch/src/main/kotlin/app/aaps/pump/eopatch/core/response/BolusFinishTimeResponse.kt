package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode

class BolusFinishTimeResponse(
    val nowBolusFinishTime: Int,
    val extBolusFinishTime: Int
) : BaseResponse(PatchBleResultCode.SUCCESS) {

    val nowBolusFinishTimeInMillis: Long get() = convertSecondToMilli(nowBolusFinishTime)
    val extBolusFinishTimeInMillis: Long get() = convertSecondToMilli(extBolusFinishTime)
}
