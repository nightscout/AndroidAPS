package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.DanaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingGlucose extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgSettingGlucose.class);

    public MsgSettingGlucose() {
        SetCommand(0x3209);
    }

    public void handleMessage(byte[] bytes) {
        DanaRFragment.getDanaRPump().units = intFromBuff(bytes, 0, 1);
        DanaRFragment.getDanaRPump().easyBasalMode = intFromBuff(bytes, 1, 1);

        if (Config.logDanaMessageDetail) {
            log.debug("Pump units: " + (DanaRFragment.getDanaRPump().units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Easy basal mode: " + DanaRFragment.getDanaRPump().easyBasalMode);
        }
    }
}
