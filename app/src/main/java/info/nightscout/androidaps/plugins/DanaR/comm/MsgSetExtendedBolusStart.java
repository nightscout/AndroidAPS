package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

public class MsgSetExtendedBolusStart extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgBolusStart.class);

    public MsgSetExtendedBolusStart() {
        SetCommand(0x0407);
    }

    public MsgSetExtendedBolusStart(double amount, byte halfhours) {
        this();
        AddParamInt((int) (amount * 100));
        AddParamByte(halfhours);
        if (Config.logDanaMessageDetail)
            log.debug("Set extended bolus start: " + amount + " halfhours " + halfhours);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            log.debug("Set extended bolus start result: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Set extended bolus start result: " + result);
        }
    }
}
