package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.ModelNameResponse
import io.reactivex.rxjava3.core.Single

class GetModelName : BaseAPI<ModelNameResponse>(PatchFunc.GET_MODEL_NAME) {
    override fun parse(bytes: ByteArray): ModelNameResponse {
        val success = bytes[DATA0].toInt() == 0
        if (!success) return ModelNameResponse(false, byteArrayOf())
        val name = ByteArray(16)
        System.arraycopy(bytes, DATA1, name, 0, 16)
        return ModelNameResponse(true, name)
    }

    fun get(): Single<ModelNameResponse> = writeAndRead(generate())
}
