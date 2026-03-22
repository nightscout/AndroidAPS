package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.TemperatureResponse
import io.reactivex.rxjava3.core.Single

class GetTemperature : BaseAPI<TemperatureResponse>(PatchFunc.GET_TEMPERATURE) {
    override fun parse(bytes: ByteArray) = TemperatureResponse(bytes[DATA0].toInt())
    fun get(): Single<TemperatureResponse> = writeAndRead(generate())
}
