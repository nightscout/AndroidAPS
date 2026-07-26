package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.WakeUpTimeResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class GetWakeUpTime @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<WakeUpTimeResponse>(PatchFunc.GET_WAKE_UP_TIME, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): WakeUpTimeResponse {
        val success = bytes[DATA0].toInt() == 0
        if (!success) return WakeUpTimeResponse(false, 0)
        return WakeUpTimeResponse(true, BytesConverter.toUInt(bytes, DATA1))
    }

    fun get(): Single<WakeUpTimeResponse> = writeAndRead(generate())
}
