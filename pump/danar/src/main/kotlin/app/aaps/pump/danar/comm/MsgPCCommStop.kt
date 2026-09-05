package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.di.MetroMemberInjector

class MsgPCCommStop(
    injector: MetroMemberInjector
) : MessageBase(injector) {

    init {
        setCommand(0x3002)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "PC comm stop received")
    }
}