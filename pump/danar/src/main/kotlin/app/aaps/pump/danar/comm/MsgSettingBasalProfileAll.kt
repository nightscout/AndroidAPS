package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import java.util.Locale

/**
 * Created by mike on 05.07.2016.
 *
 *
 *
 *
 * THIS IS BROKEN IN PUMP... SENDING ONLY 1 PROFILE
 */
class MsgSettingBasalProfileAll(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    override fun handleMessage(bytes: ByteArray) {
        danaPump.pumpProfiles = Array(4) { Array(48) { 0.0 } }
        if (danaPump.basal48Enable) {
            for (profile in 0..3) {
                val position = intFromBuff(bytes, 107 * profile, 1)
                for (index in 0..47) {
                    var basal = intFromBuff(bytes, 107 * profile + 2 * index + 1, 2)
                    if (basal < 10) basal = 0
                    danaPump.pumpProfiles!![position][index] = basal / 100.0
                }
            }
        } else {
            for (profile in 0..3) {
                val position = intFromBuff(bytes, 49 * profile, 1)
                for (index in 0..23) {
                    var basal = intFromBuff(bytes, 59 * profile + 2 * index + 1, 2)
                    if (basal < 10) basal = 0
                    aapsLogger.debug(LTag.PUMPCOMM, "position $position index $index")
                    danaPump.pumpProfiles!![position][index] = basal / 100.0
                }
            }
        }
        if (danaPump.basal48Enable) {
            for (profile in 0..3) {
                for (index in 0..47) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Basal profile " + profile + ": " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + danaPump.pumpProfiles!![profile][index])
                }
            }
        } else {
            for (profile in 0..3) {
                for (index in 0..23) { //this is absurd  danaRPump.pumpProfiles[profile][index] returns nullPointerException
                    aapsLogger.debug(
                        LTag.PUMPCOMM, "Basal profile " + profile + ": " + String.format(Locale.ENGLISH, "%02d", index / 2) +
                            ":" + String.format(Locale.ENGLISH, "%02d", index % 2 * 30) + " : " +
                            danaPump.pumpProfiles!![profile][index]
                    )
                }
            }
        }
    }

    init {
        setCommand(0x3206)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
}