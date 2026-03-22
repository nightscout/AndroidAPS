package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single

class BasalResume : BaseBooleanAPI(PatchFunc.RESUME_NORMAL_BASAL) {
    override fun generate(): ByteArray = allocate().putByte(DEFAULT).build()
    fun resume(): Single<PatchBooleanResponse> = writeAndRead(generate())

    companion object {
        const val DEFAULT = 0
    }
}
