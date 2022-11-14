package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.LTag


class MsgHistoryDone(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x31F1)
        danaPump.historyDoneReceived = false
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.historyDoneReceived = true
        aapsLogger.debug(LTag.PUMPCOMM, "History done received")
    }
}