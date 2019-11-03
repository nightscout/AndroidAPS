package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

/**
 * Created by Rumen Georgiev on 6/11/2018.
 */

public class MsgSettingUserOptions extends MessageBase {

    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSettingUserOptions() {
        SetCommand(0x320B);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] packet) {
        DanaRPump pump = DanaRPump.getInstance();
        byte[] bytes = getDataBytes(packet, 0, packet.length - 10);
        pump.userOptionsFrompump = Arrays.copyOf(bytes, bytes.length);// saving pumpDataBytes to use it in MsgSetUserOptions
        for(int pos=0; pos < bytes.length; pos++) {
            log.debug("[" + pos + "]" + bytes[pos]);
        }
        pump.timeDisplayType = bytes[0] == (byte) 1 ? 0 : 1; // 1 -> 24h 0 -> 12h
        pump.buttonScrollOnOff = bytes[1] == (byte) 1 ? 1 : 0; // 1 -> ON, 0-> OFF
        pump.beepAndAlarm = bytes[2]; // 1 -> Sound on alarm 2-> Vibrate on alarm 3-> Both on alarm 5-> Sound + beep 6-> vibrate + beep 7-> both + beep Beep adds 4
        pump.lcdOnTimeSec = bytes[3] & 255;
        pump.backlightOnTimeSec = bytes[4] & 255;
        pump.selectedLanguage = bytes[5]; // on DanaRv2 is that needed ?
        pump.units = bytes[8];
        pump.shutdownHour = bytes[9];
        pump.lowReservoirRate = bytes[32] & 255;
        /* int selectableLanguage1 = bytes[10];
        int selectableLanguage2 = bytes[11];
        int selectableLanguage3 = bytes[12];
        int selectableLanguage4 = bytes[13];
        int selectableLanguage5 = bytes[14];
        */

        if (L.isEnabled(L.PUMPCOMM)) {

            log.debug("timeDisplayType: " + pump.timeDisplayType);
            log.debug("Button scroll: " + pump.buttonScrollOnOff);
            log.debug("BeepAndAlarm: " + pump.beepAndAlarm);
            log.debug("screen timeout: " + pump.lcdOnTimeSec);
            log.debug("Backlight: " + pump.backlightOnTimeSec);
            log.debug("Selected language: " + pump.selectedLanguage);
            log.debug("Units: " + pump.getUnits());
            log.debug("Shutdown: " + pump.shutdownHour);
            log.debug("Low reservoir: " + pump.lowReservoirRate);
        }
    }

    public static byte[] getDataBytes(byte[] bytes, int start, int len) {
        if (bytes == null) {
            return null;
        }
        byte[] ret = new byte[len];
        System.arraycopy(bytes, start + 6, ret, 0, len);
        return ret;
    }
}
