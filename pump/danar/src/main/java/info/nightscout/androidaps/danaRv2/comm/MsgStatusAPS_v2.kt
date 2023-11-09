package info.nightscout.androidaps.danaRv2.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.comm.MessageBase

class MsgStatusAPS_v2(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0xE001)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val iob = intFromBuff(bytes, 0, 2) / 100.0
        val deliveredSoFar = intFromBuff(bytes, 2, 2) / 100.0
        danaPump.iob = iob
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered so far: $deliveredSoFar")
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump IOB: $iob")
    }
}