package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.ble.PumpCounter
import app.aaps.pump.eopatch.core.define.IPatchConstant.Companion.BOLUS_EXTENDED_DURATION_STEP
import app.aaps.pump.eopatch.core.define.IPatchConstant.Companion.EXT_BOLUS_ID
import app.aaps.pump.eopatch.core.define.IPatchConstant.Companion.NOW_BOLUS_ID
import app.aaps.pump.eopatch.core.response.ComboBolusStartResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class ComboBolusStart @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<ComboBolusStartResponse>(PatchFunc.START_COMBO_BOLUS, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): ComboBolusStartResponse {
        if (bytes[DATA0].toInt() != 0) return ComboBolusStartResponse(false, 0, 0)
        val id = BytesConverter.toUInt(bytes[DATA1], bytes[DATA2])
        val extendedId = BytesConverter.toUInt(bytes[DATA3], bytes[DATA4])
        return ComboBolusStartResponse(true, id, extendedId)
    }

    fun start(bolusNowDoseU: Float, bolusExDoseU: Float, bolusExDurationMins: Int): Single<ComboBolusStartResponse> {
        val nowCount = PumpCounter.getPumpCountShort(bolusNowDoseU)
        val exCount = PumpCounter.getPumpCountShort(bolusExDoseU)
        val duration = (bolusExDurationMins / BOLUS_EXTENDED_DURATION_STEP).toByte()
        return writeAndRead(
            allocate().putShort(NOW_BOLUS_ID).putShort(nowCount).reserved()
                .putShort(EXT_BOLUS_ID).putShort(exCount).putByte(duration).build()
        )
    }
}
