package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.DanaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingProfileRatios extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgSettingProfileRatios.class);

    public MsgSettingProfileRatios() {
        SetCommand(0x3204);
    }

    public void handleMessage(byte[] bytes) {
        if (DanaRFragment.getDanaRPump().units == DanaRPump.UNITS_MGDL) {
            DanaRFragment.getDanaRPump().currentCIR = intFromBuff(bytes, 0, 2);
            DanaRFragment.getDanaRPump().currentCF = intFromBuff(bytes, 2, 2);
            DanaRFragment.getDanaRPump().currentAI = intFromBuff(bytes, 4, 2) / 100;
            DanaRFragment.getDanaRPump().currentTarget = intFromBuff(bytes, 6, 2);
            DanaRFragment.getDanaRPump().currentAIDR = intFromBuff(bytes, 6, 1);
        } else {
            DanaRFragment.getDanaRPump().currentCIR = intFromBuff(bytes, 0, 2);
            DanaRFragment.getDanaRPump().currentCF = intFromBuff(bytes, 2, 2) / 100;
            DanaRFragment.getDanaRPump().currentAI = intFromBuff(bytes, 4, 2) / 100;
            DanaRFragment.getDanaRPump().currentTarget = intFromBuff(bytes, 6, 2) / 100;
            DanaRFragment.getDanaRPump().currentAIDR = intFromBuff(bytes, 6, 1);
        }

        if (Config.logDanaMessageDetail) {
            log.debug("Pump units (saved): " + (DanaRFragment.getDanaRPump().units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Current pump CIR: " + DanaRFragment.getDanaRPump().currentCIR);
            log.debug("Current pump CF: " + DanaRFragment.getDanaRPump().currentCF);
            log.debug("Current pump AI: " + DanaRFragment.getDanaRPump().currentAI);
            log.debug("Current pump target: " + DanaRFragment.getDanaRPump().currentTarget);
            log.debug("Current pump AIDR: " + DanaRFragment.getDanaRPump().currentAIDR);
        }
    }
}
