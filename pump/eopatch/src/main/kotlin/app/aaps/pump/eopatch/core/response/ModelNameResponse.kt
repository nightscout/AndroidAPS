package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode

class ModelNameResponse(success: Boolean, modelNameBytes: ByteArray) :
    BaseResponse(if (success) PatchBleResultCode.SUCCESS else PatchBleResultCode.UNKNOWN_ERROR) {

    val modelName: String = if (success) {
        val end = modelNameBytes.indexOfFirst { it == 0.toByte() }.takeIf { it >= 0 } ?: modelNameBytes.size
        String(modelNameBytes, 0, end, Charsets.UTF_8)
    } else ""
}
