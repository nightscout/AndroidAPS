package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketGeneralSetHistoryUploadMode @Inject constructor(
    private val aapsLogger: AAPSLogger
) : DanaRSPacket() {

    private var mode: Int = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__SET_HISTORY_UPLOAD_MODE
        aapsLogger.debug(LTag.PUMPCOMM, "New message: mode: $mode")
    }

    fun with(mode: Int) = this.also { this.mode = mode }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(1)
        request[0] = (mode and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        @Suppress("LiftReturnOrAssignment")
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override val friendlyName: String = "REVIEW__SET_HISTORY_UPLOAD_MODE"
}