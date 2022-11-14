package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.LTag


class MsgPCCommStop(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x3002)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "PC comm stop received")
    }
}