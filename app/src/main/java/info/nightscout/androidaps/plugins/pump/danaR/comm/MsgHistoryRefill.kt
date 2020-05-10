package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil

class MsgHistoryRefill(
    aapsLogger: AAPSLogger,
    rxBus: RxBusWrapper,
    dateUtil: DateUtil
) : MsgHistoryAll(aapsLogger, rxBus, dateUtil) {

    init {
        SetCommand(0x3108)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}