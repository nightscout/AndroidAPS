package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single

class BuzzerStop : BaseBooleanAPI(PatchFunc.STOP_BUZZER) {
    fun stop(): Single<PatchBooleanResponse> = writeAndRead(generate())
}
