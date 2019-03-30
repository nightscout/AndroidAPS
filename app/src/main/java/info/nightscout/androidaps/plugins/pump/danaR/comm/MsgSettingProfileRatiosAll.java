package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingProfileRatiosAll extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSettingProfileRatiosAll() {
        SetCommand(0x320D);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        if (pump.units == DanaRPump.UNITS_MGDL) {
            pump.morningCIR = intFromBuff(bytes, 0, 2);
            pump.morningCF = intFromBuff(bytes, 2, 2);
            pump.afternoonCIR = intFromBuff(bytes, 4, 2);
            pump.afternoonCF = intFromBuff(bytes, 6, 2);
            pump.eveningCIR = intFromBuff(bytes, 8, 2);
            pump.eveningCF = intFromBuff(bytes, 10, 2);
            pump.nightCIR = intFromBuff(bytes, 12, 2);
            pump.nightCF = intFromBuff(bytes, 14, 2);
        } else {
            pump.morningCIR = intFromBuff(bytes, 0, 2);
            pump.morningCF = intFromBuff(bytes, 2, 2) / 100d;
            pump.afternoonCIR = intFromBuff(bytes, 4, 2);
            pump.afternoonCF = intFromBuff(bytes, 6, 2) / 100d;
            pump.eveningCIR = intFromBuff(bytes, 8, 2);
            pump.eveningCF = intFromBuff(bytes, 10, 2) / 100d;
            pump.nightCIR = intFromBuff(bytes, 12, 2);
            pump.nightCF = intFromBuff(bytes, 14, 2) / 100d;
        }

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Pump units: " + (pump.units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Current pump morning CIR: " + pump.morningCIR);
            log.debug("Current pump morning CF: " + pump.morningCF);
            log.debug("Current pump afternoon CIR: " + pump.afternoonCIR);
            log.debug("Current pump afternoon CF: " + pump.afternoonCF);
            log.debug("Current pump evening CIR: " + pump.eveningCIR);
            log.debug("Current pump evening CF: " + pump.eveningCF);
            log.debug("Current pump night CIR: " + pump.nightCIR);
            log.debug("Current pump night CF: " + pump.nightCF);
        }

        pump.createConvertedProfile();
    }
}
