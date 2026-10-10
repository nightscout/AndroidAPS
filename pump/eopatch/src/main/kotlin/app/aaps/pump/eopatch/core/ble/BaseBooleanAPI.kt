package app.aaps.pump.eopatch.core.ble

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.core.scan.IBleDevice

abstract class BaseBooleanAPI(func: PatchFunc, patch: IBleDevice, aapsLogger: AAPSLogger) :
    BaseAPI<PatchBooleanResponse>(func, patch, aapsLogger) {

    override fun parse(bytes: ByteArray): PatchBooleanResponse = checkReturnValueZero(bytes)

    protected fun checkReturnValueZero(bytes: ByteArray) = PatchBooleanResponse(bytes[DATA0] == 0.toByte())

    protected fun parseBoolean(success: Boolean) = PatchBooleanResponse(success)
}
