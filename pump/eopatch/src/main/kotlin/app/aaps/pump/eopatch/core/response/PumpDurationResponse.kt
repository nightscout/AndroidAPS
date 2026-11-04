package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode

class PumpDurationResponse(
    val durationS: Int,
    val durationL: Int,
    val durationM: Int
) : BaseResponse(PatchBleResultCode.SUCCESS)
