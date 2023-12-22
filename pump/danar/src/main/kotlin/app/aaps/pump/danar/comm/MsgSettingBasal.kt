package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import java.util.Locale

class MsgSettingBasal(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x3202)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.pumpProfiles = Array(4) { Array(48) { 0.0 } }
        for (index in 0..23) {
            var basal = intFromBuff(bytes, 2 * index, 2)
            if (basal < danaRPlugin.pumpDescription.basalMinimumRate) basal = 0
            danaPump.pumpProfiles!![danaPump.activeProfile][index] = basal / 100.0
        }
        for (index in 0..23) {
            aapsLogger.debug(LTag.PUMPCOMM, "Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + danaPump.pumpProfiles!![danaPump.activeProfile][index])
        }
    }
}