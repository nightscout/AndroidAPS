package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single

class StopAeBeep : BaseBooleanAPI(PatchFunc.STOP_AE_BEEP) {
    fun stop(aeCode: Int): Single<PatchBooleanResponse> =
        writeAndRead(allocate().putByte(aeCode).build())
}
