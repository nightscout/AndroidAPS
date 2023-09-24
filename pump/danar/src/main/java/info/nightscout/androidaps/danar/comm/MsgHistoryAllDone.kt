package info.nightscout.androidaps.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector

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