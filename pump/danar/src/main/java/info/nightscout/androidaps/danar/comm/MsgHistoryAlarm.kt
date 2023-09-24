package info.nightscout.androidaps.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector

class MsgHistoryAlarm(
    injector: HasAndroidInjector
) : MsgHistoryAll(injector) {

    init {
        setCommand(0x3105)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}