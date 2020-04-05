package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class MsgSettingActiveProfile(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x320C)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaRPump.activeProfile = intFromBuff(bytes, 0, 1)
        aapsLogger.debug(LTag.PUMPCOMM, "Active profile number: " + danaRPump.activeProfile)
    }

}