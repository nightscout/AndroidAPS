package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingProfileRatios extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingProfileRatios.class);

    public MsgSettingProfileRatios() {
        SetCommand(0x3204);
    }

    public void handleMessage(byte[] bytes) {
        if (DanaRKoreanPlugin.getDanaRPump().units == DanaRKoreanPump.UNITS_MGDL) {
            DanaRKoreanPlugin.getDanaRPump().currentCIR = intFromBuff(bytes, 0, 2);
            DanaRKoreanPlugin.getDanaRPump().currentCF = intFromBuff(bytes, 2, 2);
            DanaRKoreanPlugin.getDanaRPump().currentAI = intFromBuff(bytes, 4, 2) / 100d;
            DanaRKoreanPlugin.getDanaRPump().currentTarget = intFromBuff(bytes, 6, 2);
            DanaRKoreanPlugin.getDanaRPump().currentAIDR = intFromBuff(bytes, 8, 1);
        } else {
            DanaRKoreanPlugin.getDanaRPump().currentCIR = intFromBuff(bytes, 0, 2);
            DanaRKoreanPlugin.getDanaRPump().currentCF = intFromBuff(bytes, 2, 2) / 100d;
            DanaRKoreanPlugin.getDanaRPump().currentAI = intFromBuff(bytes, 4, 2) / 100d;
            DanaRKoreanPlugin.getDanaRPump().currentTarget = intFromBuff(bytes, 6, 2) / 100d;
            DanaRKoreanPlugin.getDanaRPump().currentAIDR = intFromBuff(bytes, 8, 1);
        }

        if (Config.logDanaMessageDetail) {
            log.debug("Pump units (saved): " + (DanaRKoreanPlugin.getDanaRPump().units == DanaRKoreanPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Current pump CIR: " + DanaRKoreanPlugin.getDanaRPump().currentCIR);
            log.debug("Current pump CF: " + DanaRKoreanPlugin.getDanaRPump().currentCF);
            log.debug("Current pump AI: " + DanaRKoreanPlugin.getDanaRPump().currentAI);
            log.debug("Current pump target: " + DanaRKoreanPlugin.getDanaRPump().currentTarget);
            log.debug("Current pump AIDR: " + DanaRKoreanPlugin.getDanaRPump().currentAIDR);
        }
    }
}
