package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump

class MsgStatusProfile(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : MessageBase() {

    init {
        SetCommand(0x0204)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (danaPump.units == info.nightscout.androidaps.dana.DanaPump.UNITS_MGDL) {
            danaPump.currentCIR = intFromBuff(bytes, 0, 2)
            danaPump.currentCF = intFromBuff(bytes, 2, 2).toDouble()
            danaPump.currentAI = intFromBuff(bytes, 4, 2) / 100.0
            danaPump.currentTarget = intFromBuff(bytes, 6, 2).toDouble()
        } else {
            danaPump.currentCIR = intFromBuff(bytes, 0, 2)
            danaPump.currentCF = intFromBuff(bytes, 2, 2) / 100.0
            danaPump.currentAI = intFromBuff(bytes, 4, 2) / 100.0
            danaPump.currentTarget = intFromBuff(bytes, 6, 2) / 100.0
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units (saved): " + if (danaPump.units == info.nightscout.androidaps.dana.DanaPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump CIR: " + danaPump.currentCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump CF: " + danaPump.currentCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump AI: " + danaPump.currentAI)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump target: " + danaPump.currentTarget)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump AIDR: " + danaPump.currentAIDR)
    }
}