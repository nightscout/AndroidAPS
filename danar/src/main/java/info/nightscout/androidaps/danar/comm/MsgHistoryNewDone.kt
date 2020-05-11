package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump

class MsgHistoryNewDone(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : MessageBase() {

    init {
        SetCommand(0x42F1)
        danaPump.historyDoneReceived = false
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.historyDoneReceived = true
        aapsLogger.debug(LTag.PUMPCOMM, "History new done received")
    }
}