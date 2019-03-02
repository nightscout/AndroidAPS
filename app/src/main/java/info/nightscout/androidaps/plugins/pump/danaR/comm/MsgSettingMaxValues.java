package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;


/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingMaxValues extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSettingMaxValues() {
        SetCommand(0x3205);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        pump.maxBolus = intFromBuff(bytes, 0, 2) / 100d;
        pump.maxBasal = intFromBuff(bytes, 2, 2) / 100d;
        pump.maxDailyTotalUnits = intFromBuff(bytes, 4, 2) / 100;

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Max bolus: " + pump.maxBolus);
            log.debug("Max basal: " + pump.maxBasal);
            log.debug("Total daily max units: " + pump.maxDailyTotalUnits);
        }
    }

}
