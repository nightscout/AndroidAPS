package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.ble.PumpCounter
import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.response.TempBasalScheduleSetResponse
import app.aaps.pump.eopatch.core.util.FloatAdjusters
import io.reactivex.rxjava3.core.Single

@Singleton
class TempBasalScheduleStart @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<TempBasalScheduleSetResponse>(PatchFunc.START_TEMP_BASAL, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): TempBasalScheduleSetResponse {
        val ret = bytes[DATA0].toInt() and 0xFF
        val result = when (ret) {
            0    -> PatchBleResultCode.SUCCESS
            0x01 -> PatchBleResultCode.BASAL_NOT_REGISTERED
            0xF2 -> PatchBleResultCode.BASAL_INSULIN_AMOUNT_LIMIT
            0xF3 -> PatchBleResultCode.BASAL_PERCENT_RANGE_INVALID
            else -> PatchBleResultCode.UNKNOWN_ERROR
        }
        return TempBasalScheduleSetResponse(result)
    }

    fun start(durationTime: Long, doseU: Float, percentU: Int): Single<TempBasalScheduleSetResponse> {
        val timeUnit = (durationTime / 30).toByte()
        val pumpCount = if (doseU > 0) PumpCounter.getPumpCountShort(FloatAdjusters.ROUND2_BASAL_RATE(doseU)) else 0.toShort()
        return writeAndRead(allocate().putByte(timeUnit).putShort(pumpCount).putByte(percentU).build())
    }
}
