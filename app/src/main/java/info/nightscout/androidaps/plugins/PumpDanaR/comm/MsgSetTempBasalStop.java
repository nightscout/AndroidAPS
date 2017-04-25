package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

public class MsgSetTempBasalStop extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSetTempBasalStop.class);

    public MsgSetTempBasalStop() {
        SetCommand(0x0403);
        if (Config.logDanaMessageDetail)
            log.debug("Temp basal stop");
    }

    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            log.debug("Set temp basal stop result: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Set temp basal stop result: " + result);
        }
    }


}
