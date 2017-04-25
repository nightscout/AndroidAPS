package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

public class MsgSetExtendedBolusStop extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSetExtendedBolusStop.class);

    public MsgSetExtendedBolusStop() {
        SetCommand(0x0406);
        if (Config.logDanaMessageDetail)
            log.debug("Set extended bolus stop");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            log.debug("Set extended bolus stop result: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Set extended bolus stop result: " + result);
        }
    }


}
