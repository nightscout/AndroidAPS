package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.KeyResponse
import io.reactivex.rxjava3.core.Single

class SequenceGet : BaseAPI<KeyResponse>(PatchFunc.GET_SEQ_NUM) {
    override fun parse(bytes: ByteArray): KeyResponse = KeyResponse.create(bytes[SEQ_INDEX], bytes[SEQ_INDEX + 1])
    fun get(): Single<KeyResponse> = writeAndRead(generate())

    companion object {
        private const val SEQ_INDEX = 37
    }
}
