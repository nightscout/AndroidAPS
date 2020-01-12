package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class MsgStatusBasic(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x020A)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaRPump.pumpSuspended = intFromBuff(bytes, 0, 1) == 1
        danaRPump.calculatorEnabled = intFromBuff(bytes, 1, 1) == 1
        danaRPump.dailyTotalUnits = intFromBuff(bytes, 2, 3) / 750.0
        danaRPump.maxDailyTotalUnits = intFromBuff(bytes, 5, 2) / 100
        danaRPump.reservoirRemainingUnits = intFromBuff(bytes, 7, 3) / 750.0
        danaRPump.bolusBlocked = intFromBuff(bytes, 10, 1) == 1
        danaRPump.currentBasal = intFromBuff(bytes, 11, 2) / 100.0
        // removed. info taken from tempstatus message
        //pump.tempBasalPercent = intFromBuff(bytes, 13, 1);
        //pump.isExtendedInProgress = intFromBuff(bytes, 14, 1) == 1;
        //pump.isTempBasalInProgress = intFromBuff(bytes, 15, 1) == 1;
        danaRPump.batteryRemaining = intFromBuff(bytes, 20, 1)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump suspended: " + danaRPump.pumpSuspended)
        aapsLogger.debug(LTag.PUMPCOMM, "Calculator enabled: " + danaRPump.calculatorEnabled)
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total units: " + danaRPump.dailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Max daily total units: " + danaRPump.maxDailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir remaining units: " + danaRPump.reservoirRemainingUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus blocked: " + danaRPump.bolusBlocked)
        aapsLogger.debug(LTag.PUMPCOMM, "Current basal: " + danaRPump.currentBasal)
        //aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal percent: " + pump.tempBasalPercent);
        //aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: " + pump.isExtendedInProgress);
        //aapsLogger.debug(LTag.PUMPCOMM, "Is temp basal running: " + pump.isTempBasalInProgress);
    }
}