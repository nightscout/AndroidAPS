package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingBasal extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSettingBasal() {
        SetCommand(0x3202);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        if (pump.pumpProfiles == null) pump.pumpProfiles = new double[4][];
        pump.pumpProfiles[pump.activeProfile] = new double[24];
        for (int index = 0; index < 24; index++) {
            int basal = intFromBuff(bytes, 2 * index, 2);
            if (basal < DanaRPlugin.getPlugin().pumpDescription.basalMinimumRate) basal = 0;
            pump.pumpProfiles[pump.activeProfile][index] = basal / 100d;
        }

        if (L.isEnabled(L.PUMPCOMM))
            for (int index = 0; index < 24; index++) {
                log.debug("Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + pump.pumpProfiles[pump.activeProfile][index]);
            }
    }
}
