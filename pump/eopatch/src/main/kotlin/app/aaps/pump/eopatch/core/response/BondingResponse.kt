package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode

class BondingResponse(
    code: PatchBleResultCode,
    internal val state: Int
) : BaseResponse(code) {

    override fun toString(): String = "BondingResponse{state=$state}"
}
