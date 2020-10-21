package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag

class MsgHistoryNew(
    injector: HasAndroidInjector
) : MsgHistoryAll(injector) {

    init {
        SetCommand(0x42F2)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}