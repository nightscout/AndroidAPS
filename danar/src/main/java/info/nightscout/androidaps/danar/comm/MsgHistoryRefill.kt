package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil

class MsgHistoryRefill(
    aapsLogger: AAPSLogger,
    rxBus: RxBusWrapper,
    dateUtil: DateUtil,
    databaseHelper: DatabaseHelperInterface
) : MsgHistoryAll(aapsLogger, rxBus, dateUtil, databaseHelper) {

    init {
        SetCommand(0x3108)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}