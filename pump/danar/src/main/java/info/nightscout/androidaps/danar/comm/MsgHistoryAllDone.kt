package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.LTag


class MsgHistoryAllDone(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x41F1)
        danaPump.historyDoneReceived = false
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.historyDoneReceived = true
        aapsLogger.debug(LTag.PUMPCOMM, "History all done received")
    }
}