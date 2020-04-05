package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper

class MsgHistoryRefill(
    aapsLogger: AAPSLogger,
    rxBus: RxBusWrapper
) : MsgHistoryAll(aapsLogger, rxBus) {

    init {
        SetCommand(0x3108)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}