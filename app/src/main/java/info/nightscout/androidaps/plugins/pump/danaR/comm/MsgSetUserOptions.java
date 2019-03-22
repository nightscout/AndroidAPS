package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSetUserOptions extends MessageBase {

    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public boolean done;

    public MsgSetUserOptions() {
        SetCommand(0x330B);
        DanaRPump pump = DanaRPump.getInstance();
        if (pump.userOptionsFrompump == null) {
            // No options set -> Exitting
            log.error("NO USER OPTIONS LOADED EXITTING!");
            return;
        }
        pump.userOptionsFrompump[0] = (byte) (pump.timeDisplayType == 1 ? 0 : 1);
        pump.userOptionsFrompump[1] = (byte) pump.buttonScrollOnOff;
        pump.userOptionsFrompump[2] = (byte) pump.beepAndAlarm;
        pump.userOptionsFrompump[3] = (byte) pump.lcdOnTimeSec;
        pump.userOptionsFrompump[4] = (byte) pump.backlightOnTimeSec;
        pump.userOptionsFrompump[5] = (byte) pump.selectedLanguage;
        pump.userOptionsFrompump[8] = (byte) pump.units;
        pump.userOptionsFrompump[9] = (byte) pump.shutdownHour;
        pump.userOptionsFrompump[27] = (byte) pump.lowReservoirRate;
        for (int i = 0; i < pump.userOptionsFrompump.length; i++) {
            AddParamByte(pump.userOptionsFrompump[i]);
        }
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Setting user options: " + result + " FAILED!!!");
        } else {
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Setting user options: " + result);
        }
    }

}
