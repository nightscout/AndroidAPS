package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.ble.PumpCounter
import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.response.BasalScheduleSetResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class BasalScheduleSetBig @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<BasalScheduleSetResponse>(PatchFunc.SET_BASAL_SCHEDULE, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): BasalScheduleSetResponse {
        val ret = bytes[DATA0].toInt() and 0xFF
        val result = when (ret) {
            0    -> PatchBleResultCode.SUCCESS
            0xF2 -> PatchBleResultCode.BASAL_SCHEDULE_OVER_DOSE
            else -> PatchBleResultCode.UNKNOWN_ERROR
        }
        return BasalScheduleSetResponse(result)
    }

    fun set(doseUnitPerSegments48: FloatArray): Single<BasalScheduleSetResponse> {
        val builder = allocate(52)
        for (unit in doseUnitPerSegments48) {
            builder.putByte(PumpCounter.getPumpCount(unit).toByte())
        }
        return writeAndRead(builder.build())
    }
}
