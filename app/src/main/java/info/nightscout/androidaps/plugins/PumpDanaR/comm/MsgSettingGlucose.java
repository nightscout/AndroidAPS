package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingGlucose extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingGlucose.class);

    public MsgSettingGlucose() {
        SetCommand(0x3209);
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        pump.units = intFromBuff(bytes, 0, 1);
        pump.easyBasalMode = intFromBuff(bytes, 1, 1);

        if (Config.logDanaMessageDetail) {
            log.debug("Pump units: " + (pump.units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Easy basal mode: " + pump.easyBasalMode);
        }
    }
}
