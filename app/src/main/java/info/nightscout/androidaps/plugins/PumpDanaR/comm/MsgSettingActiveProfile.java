package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingActiveProfile extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(Constants.PUMPCOMM);

    public MsgSettingActiveProfile() {
        SetCommand(0x320C);
        if (Config.logPumpComm)
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump.getInstance().activeProfile = intFromBuff(bytes, 0, 1);

        if (Config.logPumpComm)
            log.debug("Active profile number: " + DanaRPump.getInstance().activeProfile);
    }
}
