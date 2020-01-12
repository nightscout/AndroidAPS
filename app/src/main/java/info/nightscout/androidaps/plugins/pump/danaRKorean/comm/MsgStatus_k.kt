package info.nightscout.androidaps.plugins.pump.danaRKorean.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase

class MsgStatus_k(
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
        //val lastBolusAmount = intFromBuff(bytes, 13, 2) / 100.0
        //if (lastBolusAmount != 0d) {
        //  pump.lastBolusTime = dateTimeFromBuff(bytes, 8);
        //  pump.lastBolusAmount = lastBolusAmount;
        //}
        danaRPump.iob = intFromBuff(bytes, 15, 2) / 100.0
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total: " + danaRPump.dailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: " + danaRPump.isExtendedInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus min: " + danaRPump.extendedBolusMinutes)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus amount: " + danaRPump.extendedBolusAmount)
        //aapsLogger.debug(LTag.PUMPCOMM, "Last bolus time: " + pump.lastBolusTime);
        //aapsLogger.debug(LTag.PUMPCOMM, "Last bolus amount: " + pump.lastBolusAmount);
        aapsLogger.debug(LTag.PUMPCOMM, "IOB: " + danaRPump.iob)
    }
}