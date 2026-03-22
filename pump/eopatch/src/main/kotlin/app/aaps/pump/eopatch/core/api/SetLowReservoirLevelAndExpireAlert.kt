package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class SetLowReservoirLevelAndExpireAlert @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseBooleanAPI(PatchFunc.SET_LOW_RESERVOIR, patch, aapsLogger) {
    fun set(level: Int, expireTime: Int): Single<PatchBooleanResponse> =
        writeAndRead(allocate().putByte(level).putByte(expireTime).build())
}
