package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;


public class MsgStatusBasic extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgStatusBasic() {
        SetCommand(0x020A);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();

        pump.pumpSuspended = intFromBuff(bytes, 0, 1) == 1;
        pump.calculatorEnabled = intFromBuff(bytes, 1, 1) == 1;
        pump.dailyTotalUnits = intFromBuff(bytes, 2, 3) / 750d;
        pump.maxDailyTotalUnits = intFromBuff(bytes, 5, 2) / 100;
        pump.reservoirRemainingUnits = intFromBuff(bytes, 7, 3) / 750d;
        pump.bolusBlocked = intFromBuff(bytes, 10, 1) == 1;
        pump.currentBasal = intFromBuff(bytes, 11, 2) / 100d;
        // removed. info taken from tempstatus message
        //pump.tempBasalPercent = intFromBuff(bytes, 13, 1);
        //pump.isExtendedInProgress = intFromBuff(bytes, 14, 1) == 1;
        //pump.isTempBasalInProgress = intFromBuff(bytes, 15, 1) == 1;
        pump.batteryRemaining = intFromBuff(bytes, 20, 1);

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Pump suspended: " + pump.pumpSuspended);
            log.debug("Calculator enabled: " + pump.calculatorEnabled);
            log.debug("Daily total units: " + pump.dailyTotalUnits);
            log.debug("Max daily total units: " + pump.maxDailyTotalUnits);
            log.debug("Reservoir remaining units: " + pump.reservoirRemainingUnits);
            log.debug("Bolus blocked: " + pump.bolusBlocked);
            log.debug("Current basal: " + pump.currentBasal);
            //log.debug("Current temp basal percent: " + pump.tempBasalPercent);
            //log.debug("Is extended bolus running: " + pump.isExtendedInProgress);
            //log.debug("Is temp basal running: " + pump.isTempBasalInProgress);
        }
    }
}
