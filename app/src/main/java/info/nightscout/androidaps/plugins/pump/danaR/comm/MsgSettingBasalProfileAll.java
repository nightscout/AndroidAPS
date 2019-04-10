package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;


/**
 * Created by mike on 05.07.2016.
 * <p/>
 * <p/>
 * THIS IS BROKEN IN PUMP... SENDING ONLY 1 PROFILE
 */
public class MsgSettingBasalProfileAll extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSettingBasalProfileAll() {
        SetCommand(0x3206);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
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
                    pump.pumpProfiles[position][index] = basal / 100d;
                }
            }
        } else {
            pump.pumpProfiles = new double[4][];
            for (int profile = 0; profile < 4; profile++) {
                int position = intFromBuff(bytes, 49 * profile, 1);
                pump.pumpProfiles[position] = new double[24];
                for (int index = 0; index < 24; index++) {
                    int basal = intFromBuff(bytes, 59 * profile + 2 * index + 1, 2);
                    if (basal < 10) basal = 0;
                    if (L.isEnabled(L.PUMPCOMM))
                        log.debug("position " + position + " index " + index);
                    pump.pumpProfiles[position][index] = basal / 100d;
                }
            }
        }

        if (L.isEnabled(L.PUMPCOMM)) {
            if (pump.basal48Enable) {
                for (int profile = 0; profile < 4; profile++) {
                    for (int index = 0; index < 48; index++) {
                        try {
                            log.debug("Basal profile " + profile + ": " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + pump.pumpProfiles[profile][index]);
                        } catch (Exception e){
                            log.error("Unhandled exception" , e);
                        }
                    }
                }
            } else {
                for (int profile = 0; profile < 4; profile++) {
                    for (int index = 0; index < 24; index++) {
                        //this is absurd  pump.pumpProfiles[profile][index] returns nullPointerException
                        try {
                            log.debug("Basal profile " + profile + ": " +
                                    String.format(Locale.ENGLISH, "%02d", (index / 2)) +
                                    ":" + String.format(Locale.ENGLISH, "%02d", (index % 2) * 30) + " : " +
                                    pump.pumpProfiles[profile][index]);
                        } catch (Exception e){
                            log.error("Unhandled exception" , e);
                        }
                    }
                }
            }
        }
    }

}
