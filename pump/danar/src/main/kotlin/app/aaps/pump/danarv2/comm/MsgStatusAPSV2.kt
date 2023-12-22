package app.aaps.pump.danarv2.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.danar.comm.MessageBase
import dagger.android.HasAndroidInjector

class MsgStatusAPSV2(
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