package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingUserOptions extends MessageBase {

    private int backlightOnTimeSec;
    private int beepAndAlarm;
    private int buttonScrollOnOff;
    private int cannulaVolume;
    private int error;
    private int glucoseUnit;
    private int lcdOnTimeSec;
    private int lowReservoirRate;
    private int refillRate;
    private int selectedLanguage;
    private int shutdownHour;
    private int timeDisplayType;

    public boolean done;

    public MsgSettingUserOptions(int timeDisplayType, int buttonScrollOnOff, int beepAndAlarm, int lcdOnTimeSec, int backlightOnTimeSec, int selectedLanguage, int glucoseUnit, int shutdownHour, int lowReservoirRate, int cannulaVolume, int refillRate) {
        this();
        log.debug(" initializing MsgSetUserOptions");
        log.debug("timeDisplayType: " + (byte) timeDisplayType);
        log.debug("Button scroll: " + (byte) buttonScrollOnOff);
        log.debug("BeepAndAlarm: " + (byte) beepAndAlarm);
        log.debug("screen timeout: " + (byte) lcdOnTimeSec);
        log.debug("Backlight: " + (byte) backlightOnTimeSec);
        log.debug("Selected language: " + (byte) selectedLanguage);
        log.debug("Units: " + (byte) glucoseUnit);
        log.debug("Shutdown: " + (byte) shutdownHour);
        log.debug("Low reservoir: " + (byte) lowReservoirRate);
        this.timeDisplayType = timeDisplayType;
        this.buttonScrollOnOff = buttonScrollOnOff;
        this.beepAndAlarm = beepAndAlarm;
        this.lcdOnTimeSec = lcdOnTimeSec;
        this.backlightOnTimeSec = backlightOnTimeSec;
        this.selectedLanguage = selectedLanguage;
        this.glucoseUnit = glucoseUnit;
        this.shutdownHour = shutdownHour;
        this.lowReservoirRate = lowReservoirRate;
        this.cannulaVolume = cannulaVolume;
        this.refillRate = refillRate;
        // need to organize here
        // glucoseunit is at pos 8 and lowReservoirRate is at pos 27
        AddParamByte((byte) timeDisplayType);
        AddParamByte((byte) buttonScrollOnOff);
        AddParamByte((byte) beepAndAlarm);
        AddParamByte((byte) lcdOnTimeSec);
        AddParamByte((byte) backlightOnTimeSec);
        AddParamByte((byte) selectedLanguage);
        AddParamByte((byte) glucoseUnit);
        AddParamByte((byte) shutdownHour);
        AddParamByte((byte) lowReservoirRate);
    }

    private static Logger log = LoggerFactory.getLogger(MsgSettingUserOptions.class);

    public MsgSettingUserOptions() {
        SetCommand(0x320B);
    }

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
