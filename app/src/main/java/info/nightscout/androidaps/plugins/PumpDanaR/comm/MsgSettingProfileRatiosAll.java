package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingProfileRatiosAll extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingProfileRatiosAll.class);

    public MsgSettingProfileRatiosAll() {
        SetCommand(0x320D);
    }

    public void handleMessage(byte[] bytes) {
        if (DanaRPlugin.getDanaRPump().units == DanaRPump.UNITS_MGDL) {
            DanaRPlugin.getDanaRPump().morningCIR = intFromBuff(bytes, 0, 2);
            DanaRPlugin.getDanaRPump().morningCF = intFromBuff(bytes, 2, 2);
            DanaRPlugin.getDanaRPump().afternoonCIR = intFromBuff(bytes, 4, 2);
            DanaRPlugin.getDanaRPump().afternoonCF = intFromBuff(bytes, 6, 2);
            DanaRPlugin.getDanaRPump().eveningCIR = intFromBuff(bytes, 8, 2);
            DanaRPlugin.getDanaRPump().eveningCF = intFromBuff(bytes, 10, 2);
            DanaRPlugin.getDanaRPump().nightCIR = intFromBuff(bytes, 12, 2);
            DanaRPlugin.getDanaRPump().nightCF = intFromBuff(bytes, 14, 2);
        } else {
            DanaRPlugin.getDanaRPump().morningCIR = intFromBuff(bytes, 0, 2);
            DanaRPlugin.getDanaRPump().morningCF = intFromBuff(bytes, 2, 2) / 100d;
            DanaRPlugin.getDanaRPump().afternoonCIR = intFromBuff(bytes, 4, 2);
            DanaRPlugin.getDanaRPump().afternoonCF = intFromBuff(bytes, 6, 2) / 100d;
            DanaRPlugin.getDanaRPump().eveningCIR = intFromBuff(bytes, 8, 2);
            DanaRPlugin.getDanaRPump().eveningCF = intFromBuff(bytes, 10, 2) / 100d;
            DanaRPlugin.getDanaRPump().nightCIR = intFromBuff(bytes, 12, 2);
            DanaRPlugin.getDanaRPump().nightCF = intFromBuff(bytes, 14, 2) / 100d;
        }

        if (Config.logDanaMessageDetail) {
            log.debug("Pump units: " + (DanaRPlugin.getDanaRPump().units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Current pump morning CIR: " + DanaRPlugin.getDanaRPump().morningCIR);
            log.debug("Current pump morning CF: " + DanaRPlugin.getDanaRPump().morningCF);
            log.debug("Current pump afternoon CIR: " + DanaRPlugin.getDanaRPump().afternoonCIR);
            log.debug("Current pump afternoon CF: " + DanaRPlugin.getDanaRPump().afternoonCF);
            log.debug("Current pump evening CIR: " + DanaRPlugin.getDanaRPump().eveningCIR);
            log.debug("Current pump evening CF: " + DanaRPlugin.getDanaRPump().eveningCF);
            log.debug("Current pump night CIR: " + DanaRPlugin.getDanaRPump().nightCIR);
            log.debug("Current pump night CF: " + DanaRPlugin.getDanaRPump().nightCF);
        }

        DanaRPlugin.getDanaRPump().createConvertedProfile();
    }
}
