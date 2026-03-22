package app.aaps.pump.eopatch.core.ble

import app.aaps.pump.eopatch.core.response.PatchBooleanResponse

abstract class BaseBooleanAPI(func: PatchFunc) : BaseAPI<PatchBooleanResponse>(func) {

    override fun parse(bytes: ByteArray): PatchBooleanResponse = checkReturnValueZero(bytes)

    protected fun checkReturnValueZero(bytes: ByteArray) = PatchBooleanResponse(bytes[DATA0] == 0.toByte())

    protected fun parseBoolean(success: Boolean) = PatchBooleanResponse(success)
}
