package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingBasal extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgSettingBasal.class);

    public MsgSettingBasal() {
        SetCommand(0x3202);
    }

    public void handleMessage(byte[] bytes) {
        for (int index = 0; index < 24; index++) {
            int basal = intFromBuff(bytes, 2 * index, 2);
            if (basal < 10) basal = 0;
            DanaRFragment.getDanaRPump().pumpProfiles[DanaRFragment.getDanaRPump().activeProfile].basalValue[index] = basal / 100d;
        }

        if (Config.logDanaMessageDetail)
            for (int index = 0; index < 24; index++) {
                log.debug("Basal " + String.format("%02d", index) + "h: " + DanaRFragment.getDanaRPump().pumpProfiles[DanaRFragment.getDanaRPump().activeProfile].basalValue[index]);
            }
    }
}
