package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.DanaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingProfileRatiosAll extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgSettingProfileRatiosAll.class);

    public MsgSettingProfileRatiosAll() {
        SetCommand(0x320D);
    }

    public void handleMessage(byte[] bytes) {
        if (DanaRFragment.getDanaRPump().units == DanaRPump.UNITS_MGDL) {
            DanaRFragment.getDanaRPump().morningCIR = intFromBuff(bytes, 0, 2);
            DanaRFragment.getDanaRPump().morningCF = intFromBuff(bytes, 2, 2);
            DanaRFragment.getDanaRPump().afternoonCIR = intFromBuff(bytes, 4, 2);
            DanaRFragment.getDanaRPump().afternoonCF = intFromBuff(bytes, 6, 2);
            DanaRFragment.getDanaRPump().eveningCIR = intFromBuff(bytes, 8, 2);
            DanaRFragment.getDanaRPump().eveningCF = intFromBuff(bytes, 10, 2);
            DanaRFragment.getDanaRPump().nightCIR = intFromBuff(bytes, 12, 2);
            DanaRFragment.getDanaRPump().nightCF = intFromBuff(bytes, 14, 2);
        } else {
            DanaRFragment.getDanaRPump().morningCIR = intFromBuff(bytes, 0, 2);
            DanaRFragment.getDanaRPump().morningCF = intFromBuff(bytes, 2, 2) / 100d;
            DanaRFragment.getDanaRPump().afternoonCIR = intFromBuff(bytes, 4, 2);
            DanaRFragment.getDanaRPump().afternoonCF = intFromBuff(bytes, 6, 2) / 100d;
            DanaRFragment.getDanaRPump().eveningCIR = intFromBuff(bytes, 8, 2);
            DanaRFragment.getDanaRPump().eveningCF = intFromBuff(bytes, 10, 2) / 100d;
            DanaRFragment.getDanaRPump().nightCIR = intFromBuff(bytes, 12, 2);
            DanaRFragment.getDanaRPump().nightCF = intFromBuff(bytes, 14, 2) / 100d;
        }

        if (Config.logDanaMessageDetail) {
            log.debug("Pump units: " + (DanaRFragment.getDanaRPump().units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Current pump morning CIR: " + DanaRFragment.getDanaRPump().morningCIR);
            log.debug("Current pump morning CF: " + DanaRFragment.getDanaRPump().morningCF);
            log.debug("Current pump afternoon CIR: " + DanaRFragment.getDanaRPump().afternoonCIR);
            log.debug("Current pump afternoon CF: " + DanaRFragment.getDanaRPump().afternoonCF);
            log.debug("Current pump evening CIR: " + DanaRFragment.getDanaRPump().eveningCIR);
            log.debug("Current pump evening CF: " + DanaRFragment.getDanaRPump().eveningCF);
            log.debug("Current pump night CIR: " + DanaRFragment.getDanaRPump().nightCIR);
            log.debug("Current pump night CF: " + DanaRFragment.getDanaRPump().nightCF);
        }

        DanaRFragment.getDanaRPump().createConvertedProfile();
    }
}
