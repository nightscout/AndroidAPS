package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingProfileRatios extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingProfileRatios.class);

    public MsgSettingProfileRatios() {
        SetCommand(0x3204);
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        if (pump.units == DanaRPump.UNITS_MGDL) {
            pump.currentCIR = intFromBuff(bytes, 0, 2);
            pump.currentCF = intFromBuff(bytes, 2, 2);
            pump.currentAI = intFromBuff(bytes, 4, 2) / 100d;
            pump.currentTarget = intFromBuff(bytes, 6, 2);
            pump.currentAIDR = intFromBuff(bytes, 8, 1);
        } else {
            pump.currentCIR = intFromBuff(bytes, 0, 2);
            pump.currentCF = intFromBuff(bytes, 2, 2) / 100d;
            pump.currentAI = intFromBuff(bytes, 4, 2) / 100d;
            pump.currentTarget = intFromBuff(bytes, 6, 2) / 100d;
            pump.currentAIDR = intFromBuff(bytes, 8, 1);
        }

        if (Config.logDanaMessageDetail) {
            log.debug("Pump units (saved): " + (pump.units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Current pump CIR: " + pump.currentCIR);
            log.debug("Current pump CF: " + pump.currentCF);
            log.debug("Current pump AI: " + pump.currentAI);
            log.debug("Current pump target: " + pump.currentTarget);
            log.debug("Current pump AIDR: " + pump.currentAIDR);
        }
    }
}
