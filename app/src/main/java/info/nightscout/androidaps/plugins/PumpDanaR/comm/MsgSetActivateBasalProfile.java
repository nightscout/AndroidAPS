package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

public class MsgSetActivateBasalProfile extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSetActivateBasalProfile.class);

    public MsgSetActivateBasalProfile() {
        SetCommand(0x330C);
    }

    // index 0-3
    public MsgSetActivateBasalProfile(byte index) {
        this();
        AddParamByte(index);
        if (Config.logDanaMessageDetail)
            log.debug("Activate basal profile: " + index);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            log.debug("Activate basal profile result: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Activate basal profile result: " + result);
        }
    }
}
