package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class MsgSettingProfileRatiosAll(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x320D)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (danaRPump.units == DanaRPump.UNITS_MGDL) {
            danaRPump.morningCIR = intFromBuff(bytes, 0, 2)
            danaRPump.morningCF = intFromBuff(bytes, 2, 2).toDouble()
            danaRPump.afternoonCIR = intFromBuff(bytes, 4, 2)
            danaRPump.afternoonCF = intFromBuff(bytes, 6, 2).toDouble()
            danaRPump.eveningCIR = intFromBuff(bytes, 8, 2)
            danaRPump.eveningCF = intFromBuff(bytes, 10, 2).toDouble()
            danaRPump.nightCIR = intFromBuff(bytes, 12, 2)
            danaRPump.nightCF = intFromBuff(bytes, 14, 2).toDouble()
        } else {
            danaRPump.morningCIR = intFromBuff(bytes, 0, 2)
            danaRPump.morningCF = intFromBuff(bytes, 2, 2) / 100.0
            danaRPump.afternoonCIR = intFromBuff(bytes, 4, 2)
            danaRPump.afternoonCF = intFromBuff(bytes, 6, 2) / 100.0
            danaRPump.eveningCIR = intFromBuff(bytes, 8, 2)
            danaRPump.eveningCF = intFromBuff(bytes, 10, 2) / 100.0
            danaRPump.nightCIR = intFromBuff(bytes, 12, 2)
            danaRPump.nightCF = intFromBuff(bytes, 14, 2) / 100.0
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaRPump.units == DanaRPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump morning CIR: " + danaRPump.morningCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump morning CF: " + danaRPump.morningCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump afternoon CIR: " + danaRPump.afternoonCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump afternoon CF: " + danaRPump.afternoonCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump evening CIR: " + danaRPump.eveningCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump evening CF: " + danaRPump.eveningCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump night CIR: " + danaRPump.nightCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump night CF: " + danaRPump.nightCF)
    }
}