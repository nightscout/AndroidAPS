package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.TemperatureResponse
import app.aaps.pump.eopatch.core.scan.IBleDevice
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.reactivex.rxjava3.core.Single

@SingleIn(AppScope::class)
@Inject
class GetTemperature(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<TemperatureResponse>(PatchFunc.GET_TEMPERATURE, patch, aapsLogger) {
    override fun parse(bytes: ByteArray) = TemperatureResponse(bytes[DATA0].toInt())
    fun get(): Single<TemperatureResponse> = writeAndRead(generate())
}
