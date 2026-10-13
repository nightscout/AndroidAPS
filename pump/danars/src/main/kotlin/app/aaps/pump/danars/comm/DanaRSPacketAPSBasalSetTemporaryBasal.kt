package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class DanaRSPacketAPSBasalSetTemporaryBasal @Inject constructor(
    private val aapsLogger: AAPSLogger
) : DanaRSPacket() {

    var temporaryBasalRatio = 0
    var temporaryBasalDuration = 0
    var error = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__APS_SET_TEMPORARY_BASAL
        aapsLogger.debug(LTag.PUMPCOMM, "New message: APS Temp basal start")
    }

    fun with(percent: Int) = this.also {
        temporaryBasalRatio = min(max(percent, 0), 500)
        if (percent < 100) {
            temporaryBasalDuration = PARAM30MIN
            aapsLogger.debug(LTag.PUMPCOMM, "APS Temp basal start percent: $temporaryBasalRatio duration 30 min")
        } else {
            temporaryBasalDuration = PARAM15MIN
            aapsLogger.debug(LTag.PUMPCOMM, "APS Temp basal start percent: $temporaryBasalRatio duration 15 min")
        }
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(3)
        request[0] = (temporaryBasalRatio and 0xff).toByte()
        request[1] = (temporaryBasalRatio ushr 8 and 0xff).toByte()
        request[2] = (temporaryBasalDuration and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = byteArrayToInt(getBytes(data, DATA_START, 1))
        if (result != 0) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Set APS temp basal start result: $result ERROR!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Set APS temp basal start result: $result")
        }
    }

    override val friendlyName: String = "BASAL__APS_SET_TEMPORARY_BASAL"

    companion object {

        const val PARAM30MIN = 160
        const val PARAM15MIN = 150
    }
}