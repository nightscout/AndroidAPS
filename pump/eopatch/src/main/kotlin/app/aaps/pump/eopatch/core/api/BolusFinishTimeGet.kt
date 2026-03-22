package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.BolusFinishTimeResponse
import io.reactivex.rxjava3.core.Single

class BolusFinishTimeGet : BaseAPI<BolusFinishTimeResponse>(PatchFunc.GET_BOLUS_FINISH_TIME) {
    override fun parse(bytes: ByteArray): BolusFinishTimeResponse {
        val now = BytesConverter.toUInt(bytes, DATA0)
        val ext = BytesConverter.toUInt(bytes, DATA4)
        return BolusFinishTimeResponse(now, ext)
    }

    fun get(): Single<BolusFinishTimeResponse> = writeAndRead(generate())
}
