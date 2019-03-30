package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingActiveProfile extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSettingActiveProfile() {
        SetCommand(0x320C);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump.getInstance().activeProfile = intFromBuff(bytes, 0, 1);

        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Active profile number: " + DanaRPump.getInstance().activeProfile);
    }
}
