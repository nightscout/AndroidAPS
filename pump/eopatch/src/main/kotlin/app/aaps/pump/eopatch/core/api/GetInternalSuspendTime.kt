package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchInternalSuspendTimeResponse
import app.aaps.pump.eopatch.core.scan.IBleDevice
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class GetInternalSuspendTime @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<PatchInternalSuspendTimeResponse>(PatchFunc.GET_INTERNAL_SUSPENDED_TIME, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): PatchInternalSuspendTimeResponse {
        val success = bytes[DATA0].toInt() == 0
        return if (success) PatchInternalSuspendTimeResponse(true, BytesConverter.toInt(bytes, DATA1))
        else PatchInternalSuspendTimeResponse(false, 0)
    }

    fun get(): Single<PatchInternalSuspendTimeResponse> = writeAndRead(generate())
}
