package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.LTag


class MsgHistoryAlarm(
    injector: HasAndroidInjector
) : MsgHistoryAll(injector) {

    init {
        setCommand(0x3105)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}