package info.nightscout.androidaps.danars.comm;

import android.annotation.TargetApi;
import android.os.Build;

import org.joda.time.DateTime;

import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.danars.encryption.BleEncryption;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.utils.DateUtil;

public class DanaRS_Packet {

    @Inject AAPSLogger aapsLogger;
    @Inject DateUtil dateUtil;

    protected HasAndroidInjector injector;

    private static final int TYPE_START = 0;
    private static final int OPCODE_START = 1;
    public static final int DATA_START = 2;

    private boolean received;
    public boolean failed;
    protected int type = BleEncryption.DANAR_PACKET__TYPE_RESPONSE; // most of the messages, should be changed for others
    protected int opCode;

    public DanaRS_Packet(HasAndroidInjector injector) {
        received = false;
        failed = false;
        this.injector = injector;
        injector.androidInjector().inject(this);
    }

    public boolean success() {
        return !failed;
    }
    public void setReceived() {
        received = true;
    }

    public boolean isReceived() {
        return received;
    }

    public int getType() {
        return type;
    }

    public int getOpCode() {
        return opCode;
    }

    public int getCommand() {
        return ((type & 0xFF) << 8) + (opCode & 0xFF);
    }

    public byte[] getRequestParams() {
        return null;
    }

    // STATIC FUNCTIONS

    public int getCommand(byte[] data) {
        int type = byteArrayToInt(getBytes(data, TYPE_START, 1));
        int opCode = byteArrayToInt(getBytes(data, OPCODE_START, 1));
        return ((type & 0xFF) << 8) + (opCode & 0xFF);
    }

    public void handleMessage(byte[] data) {
    }

    public void handleMessageNotReceived() {
        failed = true;
    }

    public String getFriendlyName() {
        return "UNKNOWN_PACKET";
    }

    protected byte[] getBytes(byte[] data, int srcStart, int srcLength) {
        try {
            byte[] ret = new byte[srcLength];

            System.arraycopy(data, srcStart, ret, 0, srcLength);

            return ret;
        } catch (Exception e) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Unhandled exception", e);
        }
        return null;
    }

    protected static int byteArrayToInt(byte[] b) {
        int ret;

        switch (b.length) {
            case 1:
                ret = b[0] & 0x000000FF;
                break;
            case 2:
                ret = ((b[1] & 0x000000FF) << 8) + (b[0] & 0x000000FF);
                break;
            case 3:
                ret = ((b[2] & 0x000000FF) << 16) + ((b[1] & 0x000000FF) << 8) + (b[0] & 0x000000FF);
                break;
            case 4:
                ret = ((b[3] & 0x000000FF) << 24) + ((b[2] & 0x000000FF) << 16) + ((b[1] & 0x000000FF) << 8) + (b[0] & 0x000000FF);
                break;
            default:
                ret = -1;
                break;
        }
        return ret;
    }

    public static synchronized long dateTimeSecFromBuff(byte[] buff, int offset) {
        return
                new DateTime(
                        2000 + intFromBuff(buff, offset, 1),
                        intFromBuff(buff, offset + 1, 1),
                        intFromBuff(buff, offset + 2, 1),
                        intFromBuff(buff, offset + 3, 1),
                        intFromBuff(buff, offset + 4, 1),
                        intFromBuff(buff, offset + 5, 1)
                ).getMillis();
    }

    protected static int intFromBuff(byte[] b, int srcStart, int srcLength) {
        int ret;

        switch (srcLength) {
            case 1:
                ret = b[DATA_START + srcStart + 0] & 0x000000FF;
                break;
            case 2:
                ret = ((b[DATA_START + srcStart + 1] & 0x000000FF) << 8) + (b[DATA_START + srcStart + 0] & 0x000000FF);
                break;
            case 3:
                ret = ((b[DATA_START + srcStart + 2] & 0x000000FF) << 16) + ((b[DATA_START + srcStart + 1] & 0x000000FF) << 8) + (b[DATA_START + srcStart + 0] & 0x000000FF);
                break;
            case 4:
                ret = ((b[DATA_START + srcStart + 3] & 0x000000FF) << 24) + ((b[DATA_START + srcStart + 2] & 0x000000FF) << 16) + ((b[DATA_START + srcStart + 1] & 0x000000FF) << 8) + (b[DATA_START + srcStart + 0] & 0x000000FF);
                break;
            default:
                ret = -1;
                break;
        }
        return ret;
    }

    protected static int intFromBuffMsbLsb(byte[] b, int srcStart, int srcLength) {
        int ret;

        switch (srcLength) {
            case 1:
                ret = b[DATA_START + srcStart] & 0x000000FF;
                break;
            case 2:
                ret = ((b[DATA_START + srcStart] & 0x000000FF) << 8) + (b[DATA_START + srcStart + 1] & 0x000000FF);
                break;
            case 3:
                ret = ((b[DATA_START + srcStart] & 0x000000FF) << 16) + ((b[DATA_START + srcStart + 1] & 0x000000FF) << 8) + (b[DATA_START + srcStart + 2] & 0x000000FF);
                break;
            case 4:
                ret = ((b[DATA_START + srcStart] & 0x000000FF) << 24) + ((b[DATA_START + srcStart + 1] & 0x000000FF) << 16) + ((b[DATA_START + srcStart + 2] & 0x000000FF) << 8) + (b[DATA_START + srcStart + 3] & 0x000000FF);
                break;
            default:
                ret = -1;
                break;
        }
        return ret;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String stringFromBuff(byte[] buff, int offset, int length) {
        byte[] strbuff = new byte[length];
        System.arraycopy(buff, offset, strbuff, 0, length);
        return new String(strbuff, StandardCharsets.UTF_8);
    }

    public long dateFromBuff(byte[] buff, int offset) {
        return
                new DateTime(
                        2000 + byteArrayToInt(getBytes(buff, offset, 1)),
                        byteArrayToInt(getBytes(buff, offset + 1, 1)),
                        byteArrayToInt(getBytes(buff, offset + 2, 1)),
                        0,
                        0
                ).getMillis();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)

    public static String asciiStringFromBuff(byte[] buff, int offset, int length) {
        byte[] strbuff = new byte[length];
        System.arraycopy(buff, offset, strbuff, 0, length);
        return new String(strbuff, StandardCharsets.UTF_8);
    }

    public static String toHexString(byte[] buff) {
        if (buff == null)
            return "";

        StringBuilder sb = new StringBuilder();

        int count = 0;
        for (byte element : buff) {
            sb.append(String.format("%02X ", element));
            if (++count % 4 == 0) sb.append(" ");
        }

        return sb.toString();
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static int ByteToInt(byte b) {
        return b & 0x000000FF;
    }

}
