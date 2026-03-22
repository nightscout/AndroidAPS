package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single

class SetKey : BaseBooleanAPI(PatchFunc.SET_KEY) {
    fun setKey(): Single<PatchBooleanResponse> =
        writeAndRead(allocate().putBytes(byteArrayOf(0x00, 0x00)).putBoolean(true).build())
}
