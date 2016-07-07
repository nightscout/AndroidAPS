package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingActiveProfile extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgSettingBasal.class);

    public MsgSettingActiveProfile() {
        SetCommand(0x320C);
    }

    public void handleMessage(byte[] bytes) {
        DanaRFragment.getDanaRPump().activeProfile = intFromBuff(bytes, 0, 1);

        if (Config.logDanaMessageDetail)
            log.debug("Active profile number: " + DanaRFragment.getDanaRPump().activeProfile);
    }
}
