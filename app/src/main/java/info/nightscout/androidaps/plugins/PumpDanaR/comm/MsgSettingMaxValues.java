package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;


/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingMaxValues extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingMaxValues.class);

    public MsgSettingMaxValues() {
        SetCommand(0x3205);
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        pump.maxBolus = intFromBuff(bytes, 0, 2) / 100d;
        pump.maxBasal = intFromBuff(bytes, 2, 2) / 100d;
        pump.maxDailyTotalUnits = intFromBuff(bytes, 4, 2) / 100;

        if (Config.logDanaMessageDetail) {
            log.debug("Max bolus: " + pump.maxBolus);
            log.debug("Max basal: " + pump.maxBasal);
            log.debug("Total daily max units: " + pump.maxDailyTotalUnits);
        }
    }

}
