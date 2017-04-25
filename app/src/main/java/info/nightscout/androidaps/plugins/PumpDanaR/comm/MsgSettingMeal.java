package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

/**
 * Created by mike on 13.12.2016.
 */

public class MsgSettingMeal extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingMeal.class);

    public MsgSettingMeal() {
        SetCommand(0x3203);
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPlugin.getDanaRPump();
        pump.basalStep = intFromBuff(bytes, 0, 1) / 100d;
        pump.bolusStep = intFromBuff(bytes, 1, 1) / 100d;
        boolean bolusEnabled = intFromBuff(bytes, 2, 1) == 1;
        int melodyTime = intFromBuff(bytes, 3, 1);
        int blockTime = intFromBuff(bytes, 4, 1);
        pump.isConfigUD = intFromBuff(bytes, 5, 1) == 1;

        if (Config.logDanaMessageDetail) {
            log.debug("Basal step: " + pump.basalStep);
            log.debug("Bolus step: " + pump.bolusStep);
            log.debug("Bolus enabled: " + bolusEnabled);
            log.debug("Melody time: " + melodyTime);
            log.debug("Block time: " + blockTime);
            log.debug("Is Config U/d: " + pump.isConfigUD);
        }
    }

}
