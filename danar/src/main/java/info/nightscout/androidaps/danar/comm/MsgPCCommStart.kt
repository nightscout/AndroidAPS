package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class MsgPCCommStart constructor(
    private val aapsLogger: AAPSLogger
) : MessageBase() {

    init {
        SetCommand(0x3001)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "PC comm start received")
    }
}