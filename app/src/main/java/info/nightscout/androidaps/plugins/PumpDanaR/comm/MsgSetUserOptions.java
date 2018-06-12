package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSetUserOptions extends MessageBase {

    public boolean done;

    public MsgSetUserOptions() {
        SetCommand(0x330B);

        DanaRPump pump = DanaRPump.getInstance();

        log.debug(" initializing MsgSetUserOptions");
        log.debug("timeDisplayType: " + (byte) pump.timeDisplayType);
        log.debug("Button scroll: " + (byte) pump.buttonScrollOnOff);
        log.debug("BeepAndAlarm: " + (byte) pump.beepAndAlarm);
        log.debug("screen timeout: " + (byte) pump.lcdOnTimeSec);
        log.debug("Backlight: " + (byte) pump.backlightOnTimeSec);
        log.debug("Selected language: " + (byte) pump.selectedLanguage);
        log.debug("Units: " + (byte) pump.units);
        log.debug("Shutdown: " + (byte) pump.shutdownHour);
        log.debug("Low reservoir: " + (byte) pump.lowReservoirRate);
        // need to organize here
        // glucoseunit is at pos 8 and lowReservoirRate is at pos 27
        AddParamByte((byte) pump.timeDisplayType);
        AddParamByte((byte) pump.buttonScrollOnOff);
        AddParamByte((byte) pump.beepAndAlarm);
        AddParamByte((byte) pump.lcdOnTimeSec);
        AddParamByte((byte) pump.backlightOnTimeSec);
        AddParamByte((byte) pump.selectedLanguage);
        AddParamByte((byte) pump.units);
        AddParamByte((byte) pump.shutdownHour);
        AddParamByte((byte) pump.lowReservoirRate);
    }

    private static Logger log = LoggerFactory.getLogger(MsgSetUserOptions.class);

    public void handleMessage(byte[] bytes) {
        log.debug("Entering handleMessage ");
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            log.debug("Setting user options: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Setting user options: " + result);
        }
    }

}
