package info.nightscout.androidaps.plugins.PumpDanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;


/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingMaxValues extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingMaxValues.class);

    public MsgSettingMaxValues() {
        SetCommand(0x3205);
    }

    public void handleMessage(byte[] bytes) {
        DanaRKoreanPlugin.getDanaRPump().maxBolus = intFromBuff(bytes, 0, 2) / 100d;
        DanaRKoreanPlugin.getDanaRPump().maxBasal = intFromBuff(bytes, 2, 2) / 100d;
        DanaRKoreanPlugin.getDanaRPump().maxDailyTotalUnits = intFromBuff(bytes, 4, 2) / 100;

        if (Config.logDanaMessageDetail) {
            log.debug("Max bolus: " + DanaRKoreanPlugin.getDanaRPump().maxBolus);
            log.debug("Max basal: " + DanaRKoreanPlugin.getDanaRPump().maxBasal);
            log.debug("Total daily max units: " + DanaRKoreanPlugin.getDanaRPump().maxDailyTotalUnits);
        }
    }

}
