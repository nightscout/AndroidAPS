package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.TemperatureResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class GetTemperature @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<TemperatureResponse>(PatchFunc.GET_TEMPERATURE, patch, aapsLogger) {
    override fun parse(bytes: ByteArray) = TemperatureResponse(bytes[DATA0].toInt())
    fun get(): Single<TemperatureResponse> = writeAndRead(generate())
}
