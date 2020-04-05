package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class MsgHistoryDone(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x31F1)
        danaRPump.historyDoneReceived = false
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaRPump.historyDoneReceived = true
        aapsLogger.debug(LTag.PUMPCOMM, "History done received")
    }
}