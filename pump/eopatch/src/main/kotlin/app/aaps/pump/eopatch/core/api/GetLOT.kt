package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.LotNumberResponse
import io.reactivex.rxjava3.core.Single

class GetLOT : BaseAPI<LotNumberResponse>(PatchFunc.GET_LOT_NUMBER) {
    override fun parse(bytes: ByteArray): LotNumberResponse {
        if (bytes[DATA0].toInt() == 0) {
            val part = if (bytes[DATA1].toInt() != 0) String(bytes, DATA1, 8, Charsets.UTF_8) else ""
            return LotNumberResponse(true, part)
        }
        return LotNumberResponse(false, "")
    }

    fun get(): Single<LotNumberResponse> = writeAndRead(allocate().putByte(0).build())
}
