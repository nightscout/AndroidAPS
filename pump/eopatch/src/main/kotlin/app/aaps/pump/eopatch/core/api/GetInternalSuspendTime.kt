package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchInternalSuspendTimeResponse
import io.reactivex.rxjava3.core.Single

class GetInternalSuspendTime : BaseAPI<PatchInternalSuspendTimeResponse>(PatchFunc.GET_INTERNAL_SUSPENDED_TIME) {
    override fun parse(bytes: ByteArray): PatchInternalSuspendTimeResponse {
        val success = bytes[DATA0].toInt() == 0
        return if (success) PatchInternalSuspendTimeResponse(true, BytesConverter.toInt(bytes, DATA1))
        else PatchInternalSuspendTimeResponse(false, 0)
    }

    fun get(): Single<PatchInternalSuspendTimeResponse> = writeAndRead(generate())
}
