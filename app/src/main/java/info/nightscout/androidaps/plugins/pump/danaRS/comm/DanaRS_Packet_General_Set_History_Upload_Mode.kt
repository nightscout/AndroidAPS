package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class DanaRS_Packet_General_Set_History_Upload_Mode(
    private val aapsLogger: AAPSLogger,
    private var mode: Int = 0
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__SET_HISTORY_UPLOAD_MODE
        aapsLogger.debug(LTag.PUMPCOMM, "New message: mode: $mode")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(1)
        request[0] = (mode and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override fun getFriendlyName(): String {
        return "REVIEW__SET_HISTORY_UPLOAD_MODE"
    }
}