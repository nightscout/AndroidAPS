package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;


/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingBasalProfileAll extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgSettingBasalProfileAll.class);

    public MsgSettingBasalProfileAll() {
        SetCommand(0x3206);
    }

    public void handleMessage(byte[] bytes) {
        if (DanaRFragment.getDanaRPump().basal48Enable) {
            for (int profile = 0; profile < 4; profile++) {
                int position = intFromBuff(bytes, 107 * profile, 1);
                for (int index = 0; index < 48; index++) {
                    int basal = intFromBuff(bytes, 107 * profile + 2 * index + 1, 2);
                    if (basal < 10) basal = 0;
                    DanaRFragment.getDanaRPump().pumpProfiles[position].basalValue[index] = basal / 100d;
                }
            }
        } else {
            for (int profile = 0; profile < 4; profile++) {
                int position = intFromBuff(bytes, 59 * profile, 1);
                for (int index = 0; index < 24; index++) {
                    int basal = intFromBuff(bytes, 59 * profile + 2 * index + 1, 2);
                    if (basal < 10) basal = 0;
                    DanaRFragment.getDanaRPump().pumpProfiles[position].basalValue[index] = basal / 100d;
                }
            }
        }

        if (Config.logDanaMessageDetail) {
            if (DanaRFragment.getDanaRPump().basal48Enable) {
                for (int profile = 0; profile < 4; profile++) {
                    for (int index = 0; index < 24; index++) {
                        log.debug("Basal profile " + profile + ": " + String.format("%02d", index) + "h: " + DanaRFragment.getDanaRPump().pumpProfiles[profile].basalValue[index]);
                    }
                }
            } else {
                for (int profile = 0; profile < 4; profile++) {
                    for (int index = 0; index < 48; index++) {
                        log.debug("Basal profile " + profile + ": " +
                                String.format("%02d", (index / 2)) +
                                ":" + String.format("%02d", (index % 2) * 30) + " : " +
                                DanaRFragment.getDanaRPump().pumpProfiles[profile].basalValue[index]);
                    }
                }
            }
        }
    }

}
