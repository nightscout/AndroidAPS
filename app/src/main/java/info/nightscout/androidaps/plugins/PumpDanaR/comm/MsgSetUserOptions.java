package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import android.support.v4.internal.view.SupportMenu;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSetUserOptions extends MessageBase {

    private static Logger log = LoggerFactory.getLogger(MsgSetUserOptions.class);

    public boolean done;

    public MsgSetUserOptions(int timeDisplayType, int buttonScrollOnOff, int beepAndAlarm, int lcdOnTimeSec, int backlightOnTimeSec, int selectedLanguage, int glucoseUnit, int shutdownHour, int lowReservoirRate, int cannulaVolume, int refillRate) {
        this();
        DanaRPump pump = DanaRPump.getInstance();
        if (pump.userOptionsFrompump.length == 0) {
            // No options set -> Exitting
            log.debug("NO USER OPTIONS LOADED EXITTING!");
            return;
        }

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
        pump.userOptionsFrompump[0] = (byte) (pump.timeDisplayType == 1 ? 0 : 1);
        pump.userOptionsFrompump[1] = (byte) pump.buttonScrollOnOff;
        pump.userOptionsFrompump[2] = (byte) pump.beepAndAlarm;
        pump.userOptionsFrompump[3] = (byte) pump.lcdOnTimeSec;
        pump.userOptionsFrompump[4] = (byte) pump.backlightOnTimeSec;
        pump.userOptionsFrompump[5] = (byte) pump.selectedLanguage;
        pump.userOptionsFrompump[8] = (byte) pump.units;
        pump.userOptionsFrompump[9] = (byte) pump.shutdownHour;
        pump.userOptionsFrompump[27] = (byte) pump.lowReservoirRate;
        for(int i=0; i<pump.userOptionsFrompump.length; i++){
//            AddParamByte(pump.userOptionsFrompump[i]);
            log.debug("rgDebug:userOptions["+i+"]="+pump.userOptionsFrompump[i]);
        }
    }

    public MsgSetUserOptions() {
        SetCommand(0x330B);
        DanaRPump pump = DanaRPump.getInstance();
        if (pump.userOptionsFrompump == null) {
            // No options set -> Exitting
            log.debug("NO USER OPTIONS LOADED EXITTING!");
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
        for(int i=0; i<pump.userOptionsFrompump.length; i++){
            AddParamByte(pump.userOptionsFrompump[i]);
            log.debug("rgDebug:userOptions["+i+"]="+pump.userOptionsFrompump[i]);
        }
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
