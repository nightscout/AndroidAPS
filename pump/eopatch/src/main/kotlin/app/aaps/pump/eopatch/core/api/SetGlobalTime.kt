package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SetGlobalTime : BaseBooleanAPI(PatchFunc.SET_GLOBAL_TIME) {
    fun set(): Single<PatchBooleanResponse> = writeAndRead(generate())

    override fun generate(): ByteArray {
        val now = System.currentTimeMillis()
        val diffSecs = (now / 1000).toInt()
        val offset = TimeZone.getDefault().getOffset(now)
        val timeZoneOffset = TimeUnit.MILLISECONDS.toMinutes(offset.toLong()).toInt() / 15
        return allocate().putInt(diffSecs).putByte(0).putByte(timeZoneOffset).build()
    }
}
