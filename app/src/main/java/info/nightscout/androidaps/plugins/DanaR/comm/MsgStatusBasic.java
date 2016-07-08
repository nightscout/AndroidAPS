package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;

public class MsgStatusBasic extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgStatusBasic.class);

    public MsgStatusBasic() {
        SetCommand(0x020A);
    }

    public void handleMessage(byte[] bytes) {
        boolean pumpSuspended = intFromBuff(bytes, 0, 1) == 1;
        boolean calculatorEnabled = intFromBuff(bytes, 1, 1) == 1;
        double dailyTotalUnits = intFromBuff(bytes, 2, 3) / 750d;
        int maxDailyTotalUnits = intFromBuff(bytes, 5, 2) / 100;
        double reservoirRemainingUnits = intFromBuff(bytes, 7, 3) / 750d;
        boolean bolusBlocked = intFromBuff(bytes, 10, 1) == 1;
        double currentBasal = intFromBuff(bytes, 11, 2) / 100d;
        int tempBasalPercent = intFromBuff(bytes, 13, 1);
        boolean isExtendedInProgress = intFromBuff(bytes, 14, 1) == 1;
        boolean isTempBasalInProgress = intFromBuff(bytes, 15, 1) == 1;
        int batteryRemaining = intFromBuff(bytes, 20, 1);

        DanaRFragment.getDanaRPump().pumpSuspended = pumpSuspended;
        DanaRFragment.getDanaRPump().calculatorEnabled = calculatorEnabled;
        DanaRFragment.getDanaRPump().dailyTotalUnits = dailyTotalUnits;
        DanaRFragment.getDanaRPump().maxDailyTotalUnits = maxDailyTotalUnits;
        DanaRFragment.getDanaRPump().reservoirRemainingUnits = reservoirRemainingUnits;
        DanaRFragment.getDanaRPump().bolusBlocked = bolusBlocked;
        DanaRFragment.getDanaRPump().currentBasal = currentBasal;
        DanaRFragment.getDanaRPump().tempBasalPercent = tempBasalPercent;
        DanaRFragment.getDanaRPump().isExtendedInProgress = isExtendedInProgress;
        DanaRFragment.getDanaRPump().isTempBasalInProgress = isTempBasalInProgress;
        DanaRFragment.getDanaRPump().batteryRemaining = batteryRemaining;

        if (Config.logDanaMessageDetail) {
            log.debug("Pump suspended: " + pumpSuspended);
            log.debug("Calculator enabled: " + calculatorEnabled);
            log.debug("Daily total units: " + dailyTotalUnits);
            log.debug("Max daily total units: " + maxDailyTotalUnits);
            log.debug("Reservoir remaining units: " + reservoirRemainingUnits);
            log.debug("Bolus blocked: " + bolusBlocked);
            log.debug("Current basal: " + currentBasal);
            log.debug("Current temp basal percent: " + tempBasalPercent);
            log.debug("Is extended bolus running: " + isExtendedInProgress);
            log.debug("Is temp basal running: " + isTempBasalInProgress);
        }
    }
}
