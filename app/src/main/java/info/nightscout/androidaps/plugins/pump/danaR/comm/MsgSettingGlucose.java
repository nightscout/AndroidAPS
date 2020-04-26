package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingGlucose extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSettingGlucose() {
        SetCommand(0x3209);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        pump.units = intFromBuff(bytes, 0, 1);
        pump.easyBasalMode = intFromBuff(bytes, 1, 1);

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Pump units: " + (pump.units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Easy basal mode: " + pump.easyBasalMode);
        }
    }
}
