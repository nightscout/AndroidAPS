package info.nightscout.androidaps.plugins.PumpDanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;


/**
 * Created by mike on 05.07.2016.
 * <p/>
 * <p/>
 * THIS IS BROKEN IN PUMP... SENDING ONLY 1 PROFILE
 */
public class MsgSettingBasalProfileAll_k extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingBasalProfileAll_k.class);

    public MsgSettingBasalProfileAll_k() {
        SetCommand(0x3206);
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        if (pump.basal48Enable) {
            pump.pumpProfiles = new double[4][];
            for (int profile = 0; profile < 4; profile++) {
                int position = intFromBuff(bytes, 107 * profile, 1);
                pump.pumpProfiles[position] = new double[48];
                for (int index = 0; index < 48; index++) {
                    int basal = intFromBuff(bytes, 107 * profile + 2 * index + 1, 2);
                    if (basal < 10) basal = 0;
                    pump.pumpProfiles[position][index] = basal / 100 / 24d; // in units/day
                }
            }
        } else {
            pump.pumpProfiles = new double[4][];
            for (int profile = 0; profile < 4; profile++) {
                int position = intFromBuff(bytes, 49 * profile, 1);
                log.debug("position " + position);
                pump.pumpProfiles[position] = new double[24];
                for (int index = 0; index < 24; index++) {
                    int basal = intFromBuff(bytes, 59 * profile + 2 * index + 1, 2);
                    if (basal < 10) basal = 0;
                    log.debug("position " + position + " index " + index);
                    pump.pumpProfiles[position][index] = basal / 100 / 24d; // in units/day
                }
            }
        }

        if (Config.logDanaMessageDetail) {
            if (pump.basal48Enable) {
                for (int profile = 0; profile < 4; profile++) {
                    for (int index = 0; index < 24; index++) {
                        log.debug("Basal profile " + profile + ": " + String.format("%02d", index) + "h: " + pump.pumpProfiles[profile][index]);
                    }
                }
            } else {
                for (int profile = 0; profile < 4; profile++) {
                    for (int index = 0; index < 48; index++) {
                        log.debug("Basal profile " + profile + ": " +
                                String.format("%02d", (index / 2)) +
                                ":" + String.format("%02d", (index % 2) * 30) + " : " +
                                pump.pumpProfiles[profile][index]);
                    }
                }
            }
        }
    }

}
