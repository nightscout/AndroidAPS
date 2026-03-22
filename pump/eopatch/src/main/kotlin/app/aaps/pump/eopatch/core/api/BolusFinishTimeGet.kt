package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.BolusFinishTimeResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class BolusFinishTimeGet @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<BolusFinishTimeResponse>(PatchFunc.GET_BOLUS_FINISH_TIME, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): BolusFinishTimeResponse {
        val now = BytesConverter.toUInt(bytes, DATA0)
        val ext = BytesConverter.toUInt(bytes, DATA4)
        return BolusFinishTimeResponse(now, ext)
    }

    fun get(): Single<BolusFinishTimeResponse> = writeAndRead(generate())
}
