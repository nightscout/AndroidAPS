package info.nightscout.androidaps.danaRKorean.comm

import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import java.util.*

class MsgSettingBasal_k(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump,
    private val danaRKoreanPlugin: DanaRKoreanPlugin
) : MessageBase() {

    init {
        SetCommand(0x3202)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.pumpProfiles = Array(4) { Array(48) { 0.0 } }
        for (index in 0..23) {
            var basal = intFromBuff(bytes, 2 * index, 2)
            if (basal < danaRKoreanPlugin.pumpDescription.basalMinimumRate) basal = 0
            danaPump.pumpProfiles!![danaPump.activeProfile][index] = basal / 100.0
        }
        for (index in 0..23)
            aapsLogger.debug(LTag.PUMPCOMM, "Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + danaPump.pumpProfiles!![danaPump.activeProfile][index])
    }
}