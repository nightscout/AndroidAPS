package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.response.BasalStopResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class BasalStop @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<BasalStopResponse>(PatchFunc.STOP_BASAL, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): BasalStopResponse {
        val resultCode = if (bytes[DATA0].toInt() == 0) PatchBleResultCode.SUCCESS else PatchBleResultCode.UNKNOWN_ERROR
        val id = BytesConverter.toUInt(bytes[DATA1], bytes[DATA2])
        val c = BytesConverter.toUInt(bytes[DATA3], bytes[DATA4])
        val w = BytesConverter.toUInt(bytes[DATA5])
        return BasalStopResponse(id, c, w, resultCode)
    }

    fun stop(): Single<BasalStopResponse> = writeAndRead(generate())
}
