package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.response.TempBasalFinishTimeResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class TempBasalFinishTimeGet @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<TempBasalFinishTimeResponse>(PatchFunc.GET_TEMP_BASAL_FINISH_TIME, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): TempBasalFinishTimeResponse {
        val ret = bytes[DATA0].toInt()
        var tempBasalFinishTime = 0
        val result = when (ret) {
            0    -> { tempBasalFinishTime = BytesConverter.toUInt(bytes, DATA1); PatchBleResultCode.SUCCESS }
            0x01 -> PatchBleResultCode.TEMP_BASAL_NOT_EXIST
            0x02 -> PatchBleResultCode.TEMP_BASAL_NOT_FINISH
            else -> PatchBleResultCode.UNKNOWN_ERROR
        }
        return TempBasalFinishTimeResponse(tempBasalFinishTime, result)
    }

    fun get(): Single<TempBasalFinishTimeResponse> = writeAndRead(generate())
}
