package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump

class MsgSettingActiveProfile(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : MessageBase() {

    init {
        SetCommand(0x320C)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.activeProfile = intFromBuff(bytes, 0, 1)
        aapsLogger.debug(LTag.PUMPCOMM, "Active profile number: " + danaPump.activeProfile)
    }

}