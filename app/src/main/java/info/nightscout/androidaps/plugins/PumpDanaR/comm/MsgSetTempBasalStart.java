package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

public class MsgSetTempBasalStart extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSetTempBasalStart.class);

    public MsgSetTempBasalStart() {
        SetCommand(0x0401);
    }

    public MsgSetTempBasalStart(int percent, int durationInHours) {
        this();

        //HARDCODED LIMITS
        if (percent < 0) percent = 0;
        if (percent > 200) percent = 200;
        if (durationInHours < 1) durationInHours = 1;
        if (durationInHours > 24) durationInHours = 24;

        AddParamByte((byte) (percent & 255));
        AddParamByte((byte) (durationInHours & 255));

        if (Config.logDanaMessageDetail)
            log.debug("Temp basal start percent: " + percent + " duration hours: " + durationInHours);
    }

    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            log.debug("Set temp basal start result: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Set temp basal start result: " + result);
        }
    }


}
