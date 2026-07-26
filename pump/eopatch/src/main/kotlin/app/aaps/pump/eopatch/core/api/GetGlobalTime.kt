package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.GlobalTimeResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class GetGlobalTime @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<GlobalTimeResponse>(PatchFunc.GET_GLOBAL_TIME, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): GlobalTimeResponse {
        val time = BytesConverter.toUInt(bytes, DATA0)
        return GlobalTimeResponse(time, bytes[DATA5].toInt())
    }

    fun get(@Suppress("UNUSED_PARAMETER") clearHistory: Boolean): Single<GlobalTimeResponse> = writeAndRead(generate())
    override fun generate(): ByteArray = allocate().putByte(0).build()
}
