package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.di.MetroMemberInjector

class MsgHistoryCarbo(
    injector: MetroMemberInjector
) : MsgHistoryAll(injector) {

    init {
        setCommand(0x3107)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}