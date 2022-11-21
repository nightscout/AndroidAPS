package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.LTag


class MsgHistoryCarbo(
    injector: HasAndroidInjector
) : MsgHistoryAll(injector) {

    init {
        setCommand(0x3107)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}