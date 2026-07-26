package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.ble.PumpCounter
import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.define.IPatchConstant.Companion.NOW_BOLUS_ID
import app.aaps.pump.eopatch.core.response.BolusResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class BolusStart @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<BolusResponse>(PatchFunc.START_NOW_BOLUS, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): BolusResponse {
        val resultCode = if (bytes[DATA0].toInt() != 0) PatchBleResultCode.BOLUS_OTHER_RUNNING else PatchBleResultCode.SUCCESS
        val id = BytesConverter.toUInt(bytes[DATA1], bytes[DATA2])
        return BolusResponse(resultCode, id)
    }

    fun start(bolusDoseU: Float): Single<BolusResponse> {
        val count = PumpCounter.getPumpCountShort(bolusDoseU)
        return writeAndRead(allocate().putShort(NOW_BOLUS_ID).putShort(count).build())
    }
}
