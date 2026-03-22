package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single

class SetLowReservoirLevelAndExpireAlert : BaseBooleanAPI(PatchFunc.SET_LOW_RESERVOIR) {
    fun set(level: Int, expireTime: Int): Single<PatchBooleanResponse> =
        writeAndRead(allocate().putByte(level).putByte(expireTime).build())
}
