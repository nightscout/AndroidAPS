package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class MsgStatus(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump

) : MessageBase() {

    init {
        SetCommand(0x020B)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaRPump.dailyTotalUnits = intFromBuff(bytes, 0, 3) / 750.0
        danaRPump.isExtendedInProgress = intFromBuff(bytes, 3, 1) == 1
        danaRPump.extendedBolusMinutes = intFromBuff(bytes, 4, 2)
        danaRPump.extendedBolusAmount = intFromBuff(bytes, 6, 2) / 100.0
        val lastBolusAmount = intFromBuff(bytes, 13, 2) / 100.0
        if (lastBolusAmount != 0.0) {
            danaRPump.lastBolusTime = dateTimeFromBuff(bytes, 8)
            danaRPump.lastBolusAmount = lastBolusAmount
        }
        danaRPump.iob = intFromBuff(bytes, 15, 2) / 100.0
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total: " + danaRPump.dailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: " + danaRPump.isExtendedInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus min: " + danaRPump.extendedBolusMinutes)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus amount: " + danaRPump.extendedBolusAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus time: " + danaRPump.lastBolusTime)
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus amount: " + danaRPump.lastBolusAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "IOB: " + danaRPump.iob)
    }
}