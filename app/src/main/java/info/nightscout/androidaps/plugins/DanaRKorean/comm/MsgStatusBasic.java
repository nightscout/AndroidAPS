package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;


public class MsgStatusBasic extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgStatusBasic.class);

    public MsgStatusBasic() {
        SetCommand(0x020A);
    }

    public void handleMessage(byte[] bytes) {
        double currentBasal = intFromBuff(bytes, 0, 2) / 100d;
        int batteryRemaining = intFromBuff(bytes, 2, 1);
        double reservoirRemainingUnits = intFromBuff(bytes, 3, 3) / 750d;
        double dailyTotalUnits = intFromBuff(bytes, 6, 3) / 750d;
        int maxDailyTotalUnits = intFromBuff(bytes, 9, 2) / 100;

        DanaRKoreanPlugin.getDanaRPump().dailyTotalUnits = dailyTotalUnits;
        DanaRKoreanPlugin.getDanaRPump().maxDailyTotalUnits = maxDailyTotalUnits;
        DanaRKoreanPlugin.getDanaRPump().reservoirRemainingUnits = reservoirRemainingUnits;
        DanaRKoreanPlugin.getDanaRPump().currentBasal = currentBasal;
        DanaRKoreanPlugin.getDanaRPump().batteryRemaining = batteryRemaining;

        if (Config.logDanaMessageDetail) {
            log.debug("Daily total units: " + dailyTotalUnits);
            log.debug("Max daily total units: " + maxDailyTotalUnits);
            log.debug("Reservoir remaining units: " + reservoirRemainingUnits);
            log.debug("Current basal: " + currentBasal);
        }
    }
}
