package info.nightscout.androidaps.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector

class MsgStatusBasic(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x020A)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.pumpSuspended = intFromBuff(bytes, 0, 1) == 1
        danaPump.calculatorEnabled = intFromBuff(bytes, 1, 1) == 1
        danaPump.dailyTotalUnits = intFromBuff(bytes, 2, 3) / 750.0
        danaPump.maxDailyTotalUnits = intFromBuff(bytes, 5, 2) / 100
        danaPump.reservoirRemainingUnits = intFromBuff(bytes, 7, 3) / 750.0
        danaPump.bolusBlocked = intFromBuff(bytes, 10, 1) == 1
        danaPump.currentBasal = intFromBuff(bytes, 11, 2) / 100.0
        // removed. info taken from tempstatus message
        //pump.tempBasalPercent = intFromBuff(bytes, 13, 1);
        //pump.isExtendedInProgress = intFromBuff(bytes, 14, 1) == 1;
        //pump.isTempBasalInProgress = intFromBuff(bytes, 15, 1) == 1;
        danaPump.batteryRemaining = intFromBuff(bytes, 20, 1)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump suspended: " + danaPump.pumpSuspended)
        aapsLogger.debug(LTag.PUMPCOMM, "Calculator enabled: " + danaPump.calculatorEnabled)
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total units: " + danaPump.dailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Max daily total units: " + danaPump.maxDailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir remaining units: " + danaPump.reservoirRemainingUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus blocked: " + danaPump.bolusBlocked)
        aapsLogger.debug(LTag.PUMPCOMM, "Current basal: " + danaPump.currentBasal)
        //aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal percent: " + pump.tempBasalPercent);
        //aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: " + pump.isExtendedInProgress);
        //aapsLogger.debug(LTag.PUMPCOMM, "Is temp basal running: " + pump.isTempBasalInProgress);
    }
}