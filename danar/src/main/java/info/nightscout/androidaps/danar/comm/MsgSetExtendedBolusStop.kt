package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class MsgSetExtendedBolusStop(
    private val aapsLogger: AAPSLogger
) : MessageBase() {

    init {
        SetCommand(0x0406)
        aapsLogger.debug(LTag.PUMPBTCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus stop result: $result FAILED!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus stop result: $result")
        }
    }
}