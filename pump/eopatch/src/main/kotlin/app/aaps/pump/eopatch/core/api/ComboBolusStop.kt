package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.response.ComboBolusStopResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class ComboBolusStop @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<ComboBolusStopResponse>(PatchFunc.STOP_COMBO_BOLUS, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): ComboBolusStopResponse {
        val ret = bytes[DATA0].toInt()
        var idNow = BytesConverter.toUInt(bytes[DATA1], bytes[DATA2])
        if (ret != 0x00 && ret != 0x01 && ret != 0x10) {
            val resultCode = if (ret == 0x11) PatchBleResultCode.BOLUS_UNKNOWN_ID else PatchBleResultCode.UNKNOWN_ERROR
            return ComboBolusStopResponse(idNow, resultCode)
        }
        val injected = BytesConverter.toUInt(bytes[DATA3], bytes[DATA4])
        val planned = BytesConverter.toUInt(bytes[DATA5])
        var idExt = BytesConverter.toUInt(bytes[DATA6], bytes[DATA7])
        val injectedEx = BytesConverter.toUInt(bytes[DATA8], bytes[DATA9])
        val plannedEx = BytesConverter.toUInt(bytes[DATA9 + 1])
        if (ret == 0x01) idNow = 0 else if (ret == 0x10) idExt = 0
        return ComboBolusStopResponse(idNow, injected, planned, idExt, injectedEx, plannedEx)
    }

    fun stop(id: Short, extendedId: Short): Single<ComboBolusStopResponse> =
        writeAndRead(allocate().putShort(id).putShort(extendedId).build())
}
