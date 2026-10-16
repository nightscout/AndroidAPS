package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag

class MsgHistorySuspend(
    injector: MetroMemberInjector
) : MsgHistoryAll(injector) {

    init {
        setCommand(0x3109)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}