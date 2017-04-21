package info.nightscout.androidaps.plugins.PumpDanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingGlucose extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingGlucose.class);

    public MsgSettingGlucose() {
        SetCommand(0x3209);
    }

    public void handleMessage(byte[] bytes) {
        DanaRKoreanPlugin.getDanaRPump().units = intFromBuff(bytes, 0, 1);
        DanaRKoreanPlugin.getDanaRPump().easyBasalMode = intFromBuff(bytes, 1, 1);

        if (Config.logDanaMessageDetail) {
            log.debug("Pump units: " + (DanaRKoreanPlugin.getDanaRPump().units == DanaRKoreanPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Easy basal mode: " + DanaRKoreanPlugin.getDanaRPump().easyBasalMode);
        }
    }
}
