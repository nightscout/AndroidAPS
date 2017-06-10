package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class MsgStatus extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgStatus.class);

    public MsgStatus() {
        SetCommand(0x020B);
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        pump.dailyTotalUnits = intFromBuff(bytes, 0, 3) / 750d;
        pump.isExtendedInProgress = intFromBuff(bytes, 3, 1) == 1;
        pump.extendedBolusMinutes = intFromBuff(bytes, 4, 2);
        pump.extendedBolusAmount = intFromBuff(bytes, 6, 2) / 100d;
        Double lastBolusAmount = intFromBuff(bytes, 13, 2) / 100d;
        if (lastBolusAmount != 0d) {
            pump.lastBolusTime = dateTimeFromBuff(bytes, 8);
            pump.lastBolusAmount = lastBolusAmount;
        }
        pump.iob = intFromBuff(bytes, 15, 2) / 100d;

        if (Config.logDanaMessageDetail) {
            log.debug("Daily total: " + pump.dailyTotalUnits);
            log.debug("Is extended bolus running: " + pump.isExtendedInProgress);
            log.debug("Extended bolus min: " + pump.extendedBolusMinutes);
            log.debug("Extended bolus amount: " + pump.extendedBolusAmount);
            log.debug("Last bolus time: " + pump.lastBolusTime);
            log.debug("Last bolus amount: " + pump.lastBolusAmount);
            log.debug("IOB: " + pump.iob);
        }
    }
}
