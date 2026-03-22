package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode

class PatchBooleanResponse(success: Boolean) :
    BaseResponse(if (success) PatchBleResultCode.SUCCESS else PatchBleResultCode.UNKNOWN_ERROR) {

    override fun toString(): String = "BooleanResponse{resultCode=$resultCode}"
}
