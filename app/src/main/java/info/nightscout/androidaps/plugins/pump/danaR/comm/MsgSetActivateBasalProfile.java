package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class MsgSetActivateBasalProfile extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSetActivateBasalProfile() {
        SetCommand(0x330C);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    // index 0-3
    public MsgSetActivateBasalProfile(byte index) {
        this();
        AddParamByte(index);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Activate basal profile: " + index);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Activate basal profile result: " + result + " FAILED!!!");
        } else {
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Activate basal profile result: " + result);
        }
    }
}
