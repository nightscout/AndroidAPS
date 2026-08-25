package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.response.BolusStopResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class BolusStop @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<BolusStopResponse>(PatchFunc.STOP_BOLUS, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): BolusStopResponse {
        val ret = bytes[DATA0].toInt()
        val id = BytesConverter.toUInt(bytes[DATA1], bytes[DATA2])
        if (ret != 0) {
            val resultCode = if (ret == 1) PatchBleResultCode.BOLUS_UNKNOWN_ID else PatchBleResultCode.UNKNOWN_ERROR
            return BolusStopResponse(id, resultCode)
        }
        val injected = BytesConverter.toUInt(bytes[DATA3], bytes[DATA4])
        val injecting = BytesConverter.toUInt(bytes[DATA5])
        val target = BytesConverter.toUInt(bytes[DATA7], bytes[DATA8])
        return BolusStopResponse(id, injected, injecting, target)
    }

    fun stop(id: Int): Single<BolusStopResponse> = writeAndRead(allocate().putShort(id).build())
}
