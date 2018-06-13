package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import android.support.v4.internal.view.SupportMenu;

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
    byte[] newOptions;
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
        newOptions[0] = (byte) (timeDisplayType == 1 ? 0 : 1);
        newOptions[1] = (byte) buttonScrollOnOff;
        newOptions[2] = (byte) beepAndAlarm;
        newOptions[3] = (byte) lcdOnTimeSec;
        newOptions[4] = (byte) backlightOnTimeSec;
        newOptions[5] = (byte) selectedLanguage;
        newOptions[8] = (byte) glucoseUnit;
        newOptions[9] = (byte) shutdownHour;
        newOptions[27] = (byte) lowReservoirRate;
        // need to organize here
        // glucoseunit is at pos 8 and lowReservoirRate is at pos 27
        // 6 extended bolus on/off
        // 10 missed bolus
    }

    private static Logger log = LoggerFactory.getLogger(MsgSettingUserOptions.class);

    public MsgSettingUserOptions() {
        SetCommand(0x320B);
    }

    public void handleMessage(byte[] bytes) {
        log.debug("Entering handleMessage ");
        newOptions = new byte[]{bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7], bytes[8], bytes[9], bytes[15], bytes[16], bytes[17], bytes[18], bytes[19], bytes[20], bytes[21], bytes[22], bytes[23], bytes[24], bytes[25], bytes[26], bytes[27], bytes[28], bytes[29], bytes[30], bytes[31], bytes[32]};
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            log.debug("Setting user options: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Setting user options: " + result);
        }
    }

    public byte[] getCommByte(int cmd, byte[] data) {
        int len = (data == null ? 0 : data.length) + 3;
        byte[] btSendData = new byte[(len + 7)];
        byte[] csr = new byte[2];
        byte[] crcData = new byte[len];
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        btSendData[0] = (byte) 126;
        btSendData[1] = (byte) 126;
        btSendData[2] = (byte) len;
        btSendData[3] = (byte) 241;
        btSendData[4] = (byte) ((cmd >> 8) & 255);
        btSendData[5] = (byte) (cmd & 255);
        if (len > 3) {
            System.arraycopy(data, 0, btSendData, 6, len - 3);
        }
        System.arraycopy(btSendData, 3, crcData, 0, len);
        int crc_result = GenerateCrc(crcData, len) & SupportMenu.USER_MASK;
        csr[0] = (byte) (crc_result & 255);
        csr[1] = (byte) ((crc_result >> 8) & 255);
        btSendData[len + 3] = csr[1];
        btSendData[len + 4] = csr[0];
        btSendData[len + 5] = (byte) 46;
        btSendData[len + 6] = (byte) 46;
        return btSendData;
    }

    public static int GenerateCrc(byte[] send_buf, int len) {
        int crc = 0;
        for (int i = 0; i < len; i++) {
            crc = Crc16(send_buf[i], crc);
        }
        return crc;
    }

    static int Crc16(byte byte1, int crc) {
        int li_crc = ((((crc >> 8) | (crc << 8)) & SupportMenu.USER_MASK) ^ (byte1 & 255)) & SupportMenu.USER_MASK;
        li_crc = (li_crc ^ ((li_crc & 255) >> 4)) & SupportMenu.USER_MASK;
        li_crc = (li_crc ^ ((li_crc << 8) << 4)) & SupportMenu.USER_MASK;
        return (li_crc ^ (((li_crc & 255) << 4) << 1)) & SupportMenu.USER_MASK;
    }

}
