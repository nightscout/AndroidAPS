package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.di.MetroMemberInjector

class MsgHistoryGlucose(
    injector: MetroMemberInjector
) : MsgHistoryAll(injector) {

    init {
        setCommand(0x3104)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}