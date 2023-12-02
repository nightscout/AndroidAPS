package info.nightscout.androidaps.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector

class MsgSettingActiveProfile(
    injector: HasAndroidInjector
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