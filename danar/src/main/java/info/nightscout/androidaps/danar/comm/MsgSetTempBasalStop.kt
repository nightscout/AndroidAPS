package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class MsgSetTempBasalStop(
    private val aapsLogger: AAPSLogger
) : MessageBase() {

    init {
        SetCommand(0x0403)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Temp basal stop")
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set temp basal stop result: $result FAILED!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set temp basal stop result: $result")
        }
    }
}