package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class MsgStatusProfile(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x0204)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (danaRPump.units == DanaRPump.UNITS_MGDL) {
            danaRPump.currentCIR = intFromBuff(bytes, 0, 2)
            danaRPump.currentCF = intFromBuff(bytes, 2, 2).toDouble()
            danaRPump.currentAI = intFromBuff(bytes, 4, 2) / 100.0
            danaRPump.currentTarget = intFromBuff(bytes, 6, 2).toDouble()
        } else {
            danaRPump.currentCIR = intFromBuff(bytes, 0, 2)
            danaRPump.currentCF = intFromBuff(bytes, 2, 2) / 100.0
            danaRPump.currentAI = intFromBuff(bytes, 4, 2) / 100.0
            danaRPump.currentTarget = intFromBuff(bytes, 6, 2) / 100.0
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units (saved): " + if (danaRPump.units == DanaRPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump CIR: " + danaRPump.currentCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump CF: " + danaRPump.currentCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump AI: " + danaRPump.currentAI)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump target: " + danaRPump.currentTarget)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump AIDR: " + danaRPump.currentAIDR)
    }
}