package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;

public class MsgStatus extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgStatus.class);

    public MsgStatus() {
        SetCommand(0x020B);
    }

    public void handleMessage(byte[] bytes) {
        DanaRFragment.getDanaRPump().dailyTotalUnits = intFromBuff(bytes, 0, 3) / 750d;
        DanaRFragment.getDanaRPump().isExtendedInProgress = intFromBuff(bytes, 3, 1) == 1;
        DanaRFragment.getDanaRPump().extendedBolusMinutes = intFromBuff(bytes, 4, 2);
        DanaRFragment.getDanaRPump().extendedBolusAmount = intFromBuff(bytes, 6, 2) / 100d;
        DanaRFragment.getDanaRPump().lastBolusTime = dateTimeFromBuff(bytes, 8);
        DanaRFragment.getDanaRPump().lastBolusAmount = intFromBuff(bytes, 13, 2) / 100d;
        DanaRFragment.getDanaRPump().iob = intFromBuff(bytes, 15, 2) / 100d;

        if (Config.logDanaMessageDetail) {
            log.debug("Daily total: " + DanaRFragment.getDanaRPump().dailyTotalUnits);
            log.debug("Is extended bolus running: " + DanaRFragment.getDanaRPump().isExtendedInProgress);
            log.debug("Extended bolus min: " + DanaRFragment.getDanaRPump().extendedBolusMinutes);
            log.debug("Extended bolus amount: " + DanaRFragment.getDanaRPump().extendedBolusAmount);
            log.debug("Last bolus time: " + DanaRFragment.getDanaRPump().lastBolusTime);
            log.debug("Last bolus amount: " + DanaRFragment.getDanaRPump().lastBolusAmount);
            log.debug("IOB: " + DanaRFragment.getDanaRPump().iob);
        }
    }
}
