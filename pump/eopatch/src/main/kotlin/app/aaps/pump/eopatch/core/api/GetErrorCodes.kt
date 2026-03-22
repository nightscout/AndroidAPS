package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.code.PatchAeCode
import app.aaps.pump.eopatch.core.response.AeCodeResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class GetErrorCodes @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<AeCodeResponse>(PatchFunc.GET_AE_CODES, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): AeCodeResponse {
        val aeCount = BytesConverter.toUInt(bytes[DATA0])
        val set = mutableSetOf<PatchAeCode>()
        for (i in 0 until ALARM_SIZE) {
            val aeCodeIdx = i * 4 + DATA1
            val alarm = bytes[aeCodeIdx].toInt() and 0xFF
            if (alarm == 0) break
            val timeOffset = ((bytes[aeCodeIdx + 1].toInt() and 0xFF) shl 16) or
                ((bytes[aeCodeIdx + 2].toInt() and 0xFF) shl 8) or
                (bytes[aeCodeIdx + 3].toInt() and 0xFF)
            when {
                alarm == 108 -> set.add(PatchAeCode.create(107, timeOffset))
                alarm >= 200 -> set.add(PatchAeCode.create(208, timeOffset))
                else         -> set.add(PatchAeCode.create(alarm, timeOffset))
            }
        }
        return AeCodeResponse(set, aeCount)
    }

    fun get(): Single<AeCodeResponse> = writeAndRead(allocate().putByte(0x20).putByte(0x00).build())

    companion object {
        private const val ALARM_SIZE = 4
    }
}
