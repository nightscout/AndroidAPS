package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgStatusProfile extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgStatusProfile.class);

    public MsgStatusProfile() {
        SetCommand(0x0204);
    }

    public void handleMessage(byte[] bytes) {
        if (DanaRPlugin.getDanaRPump().units == DanaRPump.UNITS_MGDL) {
            DanaRPlugin.getDanaRPump().currentCIR = intFromBuff(bytes, 0, 2);
            DanaRPlugin.getDanaRPump().currentCF = intFromBuff(bytes, 2, 2);
            DanaRPlugin.getDanaRPump().currentAI = intFromBuff(bytes, 4, 2) / 100d;
            DanaRPlugin.getDanaRPump().currentTarget = intFromBuff(bytes, 6, 2);
        } else {
            DanaRPlugin.getDanaRPump().currentCIR = intFromBuff(bytes, 0, 2);
            DanaRPlugin.getDanaRPump().currentCF = intFromBuff(bytes, 2, 2) / 100d;
            DanaRPlugin.getDanaRPump().currentAI = intFromBuff(bytes, 4, 2) / 100d;
            DanaRPlugin.getDanaRPump().currentTarget = intFromBuff(bytes, 6, 2) / 100d;
        }

        if (Config.logDanaMessageDetail) {
            log.debug("Pump units (saved): " + (DanaRPlugin.getDanaRPump().units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Current pump CIR: " + DanaRPlugin.getDanaRPump().currentCIR);
            log.debug("Current pump CF: " + DanaRPlugin.getDanaRPump().currentCF);
            log.debug("Current pump AI: " + DanaRPlugin.getDanaRPump().currentAI);
            log.debug("Current pump target: " + DanaRPlugin.getDanaRPump().currentTarget);
            log.debug("Current pump AIDR: " + DanaRPlugin.getDanaRPump().currentAIDR);
        }
    }
}
