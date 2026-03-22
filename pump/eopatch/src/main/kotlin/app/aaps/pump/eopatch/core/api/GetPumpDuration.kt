package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PumpDurationResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class GetPumpDuration @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<PumpDurationResponse>(PatchFunc.GET_PUMP_DURATION, patch, aapsLogger) {
    override fun parse(bytes: ByteArray) = PumpDurationResponse(
        BytesConverter.toUInt(bytes[DATA0], bytes[DATA1]),
        BytesConverter.toUInt(bytes[DATA2], bytes[DATA3]),
        BytesConverter.toUInt(bytes[DATA4], bytes[DATA5])
    )

    fun get(): Single<PumpDurationResponse> = writeAndRead(generate())
}
