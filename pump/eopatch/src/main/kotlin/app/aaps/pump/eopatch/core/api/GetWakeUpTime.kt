package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.WakeUpTimeResponse
import io.reactivex.rxjava3.core.Single

class GetWakeUpTime : BaseAPI<WakeUpTimeResponse>(PatchFunc.GET_WAKE_UP_TIME) {
    override fun parse(bytes: ByteArray): WakeUpTimeResponse {
        val success = bytes[DATA0].toInt() == 0
        if (!success) return WakeUpTimeResponse(false, 0)
        return WakeUpTimeResponse(true, BytesConverter.toUInt(bytes, DATA1))
    }

    fun get(): Single<WakeUpTimeResponse> = writeAndRead(generate())
}
