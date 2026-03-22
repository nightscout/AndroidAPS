package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.SerialNumberResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class GetSerialNumber @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<SerialNumberResponse>(PatchFunc.GET_SERIAL_NUMBER, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): SerialNumberResponse {
        if (bytes[DATA0].toInt() == 0) {
            val part = if (bytes[DATA1].toInt() != 0) String(bytes, DATA1, 11, Charsets.UTF_8) else ""
            return SerialNumberResponse(true, part)
        }
        return SerialNumberResponse(false, "")
    }

    fun get(): Single<SerialNumberResponse> = writeAndRead(allocate().putByte(0).build())
}
