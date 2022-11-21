package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.LTag


class MsgSetExtendedBolusStop(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0406)
        aapsLogger.debug(LTag.PUMPBTCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus stop result: $result FAILED!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus stop result: $result OK")
        }
    }
}