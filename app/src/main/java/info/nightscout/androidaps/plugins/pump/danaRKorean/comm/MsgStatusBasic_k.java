package info.nightscout.androidaps.plugins.pump.danaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;


public class MsgStatusBasic_k extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgStatusBasic_k() {
        SetCommand(0x020A);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        double currentBasal = intFromBuff(bytes, 0, 2) / 100d;
        int batteryRemaining = intFromBuff(bytes, 2, 1);
        double reservoirRemainingUnits = intFromBuff(bytes, 3, 3) / 750d;
        double dailyTotalUnits = intFromBuff(bytes, 6, 3) / 750d;
        int maxDailyTotalUnits = intFromBuff(bytes, 9, 2) / 100;

        pump.dailyTotalUnits = dailyTotalUnits;
        pump.maxDailyTotalUnits = maxDailyTotalUnits;
        pump.reservoirRemainingUnits = reservoirRemainingUnits;
        pump.currentBasal = currentBasal;
        pump.batteryRemaining = batteryRemaining;

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Daily total units: " + dailyTotalUnits);
            log.debug("Max daily total units: " + maxDailyTotalUnits);
            log.debug("Reservoir remaining units: " + reservoirRemainingUnits);
            log.debug("Current basal: " + currentBasal);
        }
    }
}
