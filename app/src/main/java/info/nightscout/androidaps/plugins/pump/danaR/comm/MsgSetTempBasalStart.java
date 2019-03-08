package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class MsgSetTempBasalStart extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

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

        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Temp basal start percent: " + percent + " duration hours: " + durationInHours);
    }

    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set temp basal start result: " + result + " FAILED!!!");
        } else {
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set temp basal start result: " + result);
        }
    }


}
