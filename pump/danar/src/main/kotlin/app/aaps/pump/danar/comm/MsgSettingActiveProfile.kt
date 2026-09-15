package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.di.MetroMemberInjector

class MsgSettingActiveProfile(
    injector: MetroMemberInjector
) : MessageBase(injector) {

    init {
        setCommand(0x320C)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.activeProfile = intFromBuff(bytes, 0, 1)
        aapsLogger.debug(LTag.PUMPCOMM, "Active profile number: " + danaPump.activeProfile)
    }

}