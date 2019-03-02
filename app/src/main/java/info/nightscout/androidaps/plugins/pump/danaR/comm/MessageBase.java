package info.nightscout.androidaps.plugins.pump.danaR.comm;

import android.annotation.TargetApi;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.CRC;

/*
 *  00  01   02  03   04   05  06
 *
 *  7E  7E  len  F1  CMD  SUB data CRC CRC 2E  2E
 */

public class MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    protected byte[] buffer = new byte[512];
    private int position = 6;

    public boolean received = false;
    public boolean failed = false;

    public void SetCommand(int cmd) {
        this.buffer[4] = (byte) (cmd >> 8 & 0xFF);
        this.buffer[5] = (byte) (cmd & 0xFF);
    }

    public void resetBuffer() {
        position = 6;
    }

    public void AddParamByte(byte data) {
        this.buffer[this.position++] = data;
    }

    public void AddParamInt(int data) {
        this.buffer[this.position++] = (byte) (data >> 8 & 0xFF);
        this.buffer[this.position++] = (byte) (data & 0xFF);
    }

    public void AddParamDate(GregorianCalendar date) {
        AddParamByte((byte) (date.get(Calendar.YEAR) - 1900 - 100));
        AddParamByte((byte) (date.get(Calendar.MONTH) + 1));
        AddParamByte((byte) (date.get(Calendar.DAY_OF_MONTH)));
        AddParamByte((byte) (date.get(Calendar.HOUR_OF_DAY)));
        AddParamByte((byte) (date.get(Calendar.MINUTE)));
    }

    public void AddParamDateTime(GregorianCalendar date) {
        AddParamByte((byte) (date.get(Calendar.YEAR) - 1900 - 100));
        AddParamByte((byte) (date.get(Calendar.MONTH) + 1));
        AddParamByte((byte) (date.get(Calendar.DAY_OF_MONTH)));
        AddParamByte((byte) (date.get(Calendar.HOUR_OF_DAY)));
        AddParamByte((byte) (date.get(Calendar.MINUTE)));
        AddParamByte((byte) (date.get(Calendar.SECOND)));
    }

    public void AddParamDateTime(Date date) {
        AddParamByte((byte) (date.getSeconds()));
        AddParamByte((byte) (date.getMinutes()));
        AddParamByte((byte) (date.getHours()));
        AddParamByte((byte) (date.getDate()));
        AddParamByte((byte) (date.getMonth() + 1));
        AddParamByte((byte) (date.getYear() - 100));
    }

    public byte[] getRawMessageBytes() {
        this.buffer[0] = (byte) 0x7E;
        this.buffer[1] = (byte) 0x7E;

        int length = this.position - 3;

        this.buffer[2] = (byte) length;
        this.buffer[3] = (byte) 0xF1;

        this.AddParamInt(CRC.getCrc16(this.buffer, 3, length));

        this.buffer[length + 5] = (byte) 0x2E;
        this.buffer[length + 6] = (byte) 0x2E;

        return Arrays.copyOf(buffer, length + 7);
    }

    public String getMessageName() {
        return MessageOriginalNames.getName(getCommand());
    }

    public void handleMessage(byte[] bytes) {
        if (L.isEnabled(L.PUMPCOMM)) {
            if (bytes.length > 6) {
                int command = (bytes[5] & 0xFF) | ((bytes[4] << 8) & 0xFF00);
                log.debug("UNPROCESSED MSG: " + getMessageName() + " Command: " + String.format("%04X", command) + " Data: " + toHexString(bytes));
            } else {
                log.debug("MISFORMATTED MSG: " + toHexString(bytes));
            }
        }
    }

    public int getCommand() {
        int command = byteFromRawBuff(buffer, 5) | (byteFromRawBuff(buffer, 4) << 8);
        return command;
    }

    public static int byteFromRawBuff(byte[] buff, int offset) {
        return buff[offset] & 0xFF;
    }

    public static int intFromBuff(byte[] buff, int offset, int length) {
        offset += 6;
        switch (length) {
            case 1:
                return byteFromRawBuff(buff, offset);
            case 2:
                return (byteFromRawBuff(buff, offset) << 8) + byteFromRawBuff(buff, offset + 1);
            case 3:
                return (byteFromRawBuff(buff, offset + 2) << 16) + (byteFromRawBuff(buff, offset + 1) << 8) + byteFromRawBuff(buff, offset);
            case 4:
                return (byteFromRawBuff(buff, offset + 3) << 24) + (byteFromRawBuff(buff, offset + 2) << 16) + (byteFromRawBuff(buff, offset + 1) << 8) + byteFromRawBuff(buff, offset);
        }
        return 0;
    }

    public static long dateTimeFromBuff(byte[] buff, int offset) {
        return
                new Date(
                        100 + intFromBuff(buff, offset, 1),
                        intFromBuff(buff, offset + 1, 1) - 1,
                        intFromBuff(buff, offset + 2, 1),
                        intFromBuff(buff, offset + 3, 1),
                        intFromBuff(buff, offset + 4, 1),
                        0
                ).getTime();
    }

    public static synchronized long dateTimeSecFromBuff(byte[] buff, int offset) {
        return
                new Date(
                        100 + intFromBuff(buff, offset, 1),
                        intFromBuff(buff, offset + 1, 1) - 1,
                        intFromBuff(buff, offset + 2, 1),
                        intFromBuff(buff, offset + 3, 1),
                        intFromBuff(buff, offset + 4, 1),
                        intFromBuff(buff, offset + 5, 1)
                ).getTime();
    }

    public static long dateFromBuff(byte[] buff, int offset) {
        return
                new Date(
                        100 + intFromBuff(buff, offset, 1),
                        intFromBuff(buff, offset + 1, 1) - 1,
                        intFromBuff(buff, offset + 2, 1)
                ).getTime();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String stringFromBuff(byte[] buff, int offset, int length) {
        byte[] strbuff = new byte[length];
        System.arraycopy(buff, offset + 6, strbuff, 0, length);
        return new String(strbuff, StandardCharsets.UTF_8);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String asciiStringFromBuff(byte[] buff, int offset, int length) {
        byte[] strbuff = new byte[length];
        System.arraycopy(buff, offset + 6, strbuff, 0, length);
        for (int pos = 0; pos < length; pos++)
            strbuff[pos] += 65; // "A"
        return new String(strbuff, StandardCharsets.UTF_8);
    }

    public static String toHexString(byte[] buff) {
        StringBuffer sb = new StringBuffer();

        int count = 0;
        for (byte element : buff) {
            sb.append(String.format("%02x ", element));
            if (++count % 4 == 0) sb.append(" ");
        }

        return sb.toString();
    }
}
