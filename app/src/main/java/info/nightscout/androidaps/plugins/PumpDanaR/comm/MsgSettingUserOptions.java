package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

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
