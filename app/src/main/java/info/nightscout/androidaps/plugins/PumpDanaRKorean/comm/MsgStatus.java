package info.nightscout.androidaps.plugins.PumpDanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;

public class MsgStatus extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgStatus.class);

    public MsgStatus() {
        SetCommand(0x020B);
    }

    public void handleMessage(byte[] bytes) {
        DanaRKoreanPlugin.getDanaRPump().dailyTotalUnits = intFromBuff(bytes, 0, 3) / 750d;
        DanaRKoreanPlugin.getDanaRPump().isExtendedInProgress = intFromBuff(bytes, 3, 1) == 1;
        DanaRKoreanPlugin.getDanaRPump().extendedBolusMinutes = intFromBuff(bytes, 4, 2);
        DanaRKoreanPlugin.getDanaRPump().extendedBolusAmount = intFromBuff(bytes, 6, 2) / 100d;
        Double lastBolusAmount = intFromBuff(bytes, 13, 2) / 100d;
//        if (lastBolusAmount != 0d) {
//            DanaRKoreanPlugin.getDanaRPump().lastBolusTime = dateTimeFromBuff(bytes, 8);
//            DanaRKoreanPlugin.getDanaRPump().lastBolusAmount = lastBolusAmount;
//        }
        DanaRKoreanPlugin.getDanaRPump().iob = intFromBuff(bytes, 15, 2) / 100d;

        if (Config.logDanaMessageDetail) {
            log.debug("Daily total: " + DanaRKoreanPlugin.getDanaRPump().dailyTotalUnits);
            log.debug("Is extended bolus running: " + DanaRKoreanPlugin.getDanaRPump().isExtendedInProgress);
            log.debug("Extended bolus min: " + DanaRKoreanPlugin.getDanaRPump().extendedBolusMinutes);
            log.debug("Extended bolus amount: " + DanaRKoreanPlugin.getDanaRPump().extendedBolusAmount);
//            log.debug("Last bolus time: " + DanaRKoreanPlugin.getDanaRPump().lastBolusTime);
//            log.debug("Last bolus amount: " + DanaRKoreanPlugin.getDanaRPump().lastBolusAmount);
            log.debug("IOB: " + DanaRKoreanPlugin.getDanaRPump().iob);
        }
    }
}
