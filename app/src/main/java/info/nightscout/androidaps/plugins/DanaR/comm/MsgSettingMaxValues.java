package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;


/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingMaxValues extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgSettingMaxValues.class);

    public MsgSettingMaxValues() {
        SetCommand(0x3205);
    }

    public void handleMessage(byte[] bytes) {
        DanaRFragment.getDanaRPump().maxBolus = intFromBuff(bytes, 0, 2) / 100d;
        DanaRFragment.getDanaRPump().maxBasal = intFromBuff(bytes, 2, 2) / 100d;
        DanaRFragment.getDanaRPump().dailyMax = intFromBuff(bytes, 4, 2) / 100d;

        if (Config.logDanaMessageDetail) {
            log.debug("Max bolus: " + DanaRFragment.getDanaRPump().maxBolus);
            log.debug("Max basal: " + DanaRFragment.getDanaRPump().maxBasal);
            log.debug("Max daily max: " + DanaRFragment.getDanaRPump().dailyMax);
        }
    }

}
