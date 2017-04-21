package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;

public class MsgStatus extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgStatus.class);

    public MsgStatus() {
        SetCommand(0x020B);
    }

    public void handleMessage(byte[] bytes) {
        DanaRPlugin.getDanaRPump().dailyTotalUnits = intFromBuff(bytes, 0, 3) / 750d;
        DanaRPlugin.getDanaRPump().isExtendedInProgress = intFromBuff(bytes, 3, 1) == 1;
        DanaRPlugin.getDanaRPump().extendedBolusMinutes = intFromBuff(bytes, 4, 2);
        DanaRPlugin.getDanaRPump().extendedBolusAmount = intFromBuff(bytes, 6, 2) / 100d;
        Double lastBolusAmount = intFromBuff(bytes, 13, 2) / 100d;
        if (lastBolusAmount != 0d) {
            DanaRPlugin.getDanaRPump().lastBolusTime = dateTimeFromBuff(bytes, 8);
            DanaRPlugin.getDanaRPump().lastBolusAmount = lastBolusAmount;
        }
        DanaRPlugin.getDanaRPump().iob = intFromBuff(bytes, 15, 2) / 100d;

        if (Config.logDanaMessageDetail) {
            log.debug("Daily total: " + DanaRPlugin.getDanaRPump().dailyTotalUnits);
            log.debug("Is extended bolus running: " + DanaRPlugin.getDanaRPump().isExtendedInProgress);
            log.debug("Extended bolus min: " + DanaRPlugin.getDanaRPump().extendedBolusMinutes);
            log.debug("Extended bolus amount: " + DanaRPlugin.getDanaRPump().extendedBolusAmount);
            log.debug("Last bolus time: " + DanaRPlugin.getDanaRPump().lastBolusTime);
            log.debug("Last bolus amount: " + DanaRPlugin.getDanaRPump().lastBolusAmount);
            log.debug("IOB: " + DanaRPlugin.getDanaRPump().iob);
        }
    }
}
