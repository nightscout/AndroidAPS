package info.nightscout.pump.diaconn.packet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import info.nightscout.shared.utils.DateUtil;

public class DiaconnG8Packet {

    @Inject AAPSLogger aapsLogger;
    @Inject DateUtil dateUtil;

    protected HasAndroidInjector injector;
    private boolean received;
    public boolean failed;
    public byte msgType;

    public static final int MSG_LEN = 20; // 메시지 길이(20바이트 패킷)
    public static final int MSG_LEN_BIG = 182; // 메시지 길이(182바이트 대량패킷)
    public static final byte SOP = (byte) 0xef; // 패킷 시작 바이트(20바이트 패킷)
    public static final byte SOP_BIG =  (byte) 0xed; // 대량 패킷 시작 바이트(182바이트 대량패킷)
    public static final byte MSG_TYPE_LOC = 1; // 메시지 종류 위치
    public static final byte MSG_SEQ_LOC = 2; // 메시지 시퀀스번호 위치
    public static final byte BT_MSG_DATA_LOC = 4; // 데이터 위치
    public static final byte MSG_PAD =  (byte) 0xff; // 메시지 뒷부분 빈공간을 채우는 값
    public static final byte MSG_CON_END =  (byte) 0x00; // 패킷 내용 끝
    public static final byte MSG_CON_CONTINUE =  (byte) 0x01; // 패킷 내용 계속

    /**
     *  CRC 정보
     */
    private static final byte[] crc_table = {
        (byte) 0x00, (byte) 0x25, (byte) 0x4A, (byte) 0x6F, (byte) 0x94, (byte) 0xB1, (byte) 0xDE, (byte) 0xFB,
        (byte) 0x0D, (byte) 0x28, (byte) 0x47, (byte) 0x62, (byte) 0x99, (byte) 0xBC, (byte) 0xD3, (byte) 0xF6,
        (byte) 0x1A, (byte) 0x3F, (byte) 0x50, (byte) 0x75, (byte) 0x8E, (byte) 0xAB, (byte) 0xC4, (byte) 0xE1,
        (byte) 0x17, (byte) 0x32, (byte) 0x5D, (byte) 0x78, (byte) 0x83, (byte) 0xA6, (byte) 0xC9, (byte) 0xEC,
        (byte) 0x34, (byte) 0x11, (byte) 0x7E, (byte) 0x5B, (byte) 0xA0, (byte) 0x85, (byte) 0xEA, (byte) 0xCF,
        (byte) 0x39, (byte) 0x1C, (byte) 0x73, (byte) 0x56, (byte) 0xAD, (byte) 0x88, (byte) 0xE7, (byte) 0xC2,
        (byte) 0x2E, (byte) 0x0B, (byte) 0x64, (byte) 0x41, (byte) 0xBA, (byte) 0x9F, (byte) 0xF0, (byte) 0xD5,
        (byte) 0x23, (byte) 0x06, (byte) 0x69, (byte) 0x4C, (byte) 0xB7, (byte) 0x92, (byte) 0xFD, (byte) 0xD8,
        (byte) 0x68, (byte) 0x4D, (byte) 0x22, (byte) 0x07, (byte) 0xFC, (byte) 0xD9, (byte) 0xB6, (byte) 0x93,
        (byte) 0x65, (byte) 0x40, (byte) 0x2F, (byte) 0x0A, (byte) 0xF1, (byte) 0xD4, (byte) 0xBB, (byte) 0x9E,
        (byte) 0x72, (byte) 0x57, (byte) 0x38, (byte) 0x1D, (byte) 0xE6, (byte) 0xC3, (byte) 0xAC, (byte) 0x89,
        (byte) 0x7F, (byte) 0x5A, (byte) 0x35, (byte) 0x10, (byte) 0xEB, (byte) 0xCE, (byte) 0xA1, (byte) 0x84,
        (byte) 0x5C, (byte) 0x79, (byte) 0x16, (byte) 0x33, (byte) 0xC8, (byte) 0xED, (byte) 0x82, (byte) 0xA7,
        (byte) 0x51, (byte) 0x74, (byte) 0x1B, (byte) 0x3E, (byte) 0xC5, (byte) 0xE0, (byte) 0x8F, (byte) 0xAA,
        (byte) 0x46, (byte) 0x63, (byte) 0x0C, (byte) 0x29, (byte) 0xD2, (byte) 0xF7, (byte) 0x98, (byte) 0xBD,
        (byte) 0x4B, (byte) 0x6E, (byte) 0x01, (byte) 0x24, (byte) 0xDF, (byte) 0xFA, (byte) 0x95, (byte) 0xB0,
        (byte) 0xD0, (byte) 0xF5, (byte) 0x9A, (byte) 0xBF, (byte) 0x44, (byte) 0x61, (byte) 0x0E, (byte) 0x2B,
        (byte) 0xDD, (byte) 0xF8, (byte) 0x97, (byte) 0xB2, (byte) 0x49, (byte) 0x6C, (byte) 0x03, (byte) 0x26,
        (byte) 0xCA, (byte) 0xEF, (byte) 0x80, (byte) 0xA5, (byte) 0x5E, (byte) 0x7B, (byte) 0x14, (byte) 0x31,
        (byte) 0xC7, (byte) 0xE2, (byte) 0x8D, (byte) 0xA8, (byte) 0x53, (byte) 0x76, (byte) 0x19, (byte) 0x3C,
        (byte) 0xE4, (byte) 0xC1, (byte) 0xAE, (byte) 0x8B, (byte) 0x70, (byte) 0x55, (byte) 0x3A, (byte) 0x1F,
        (byte) 0xE9, (byte) 0xCC, (byte) 0xA3, (byte) 0x86, (byte) 0x7D, (byte) 0x58, (byte) 0x37, (byte) 0x12,
        (byte) 0xFE, (byte) 0xDB, (byte) 0xB4, (byte) 0x91, (byte) 0x6A, (byte) 0x4F, (byte) 0x20, (byte) 0x05,
        (byte) 0xF3, (byte) 0xD6, (byte) 0xB9, (byte) 0x9C, (byte) 0x67, (byte) 0x42, (byte) 0x2D, (byte) 0x08,
        (byte) 0xB8, (byte) 0x9D, (byte) 0xF2, (byte) 0xD7, (byte) 0x2C, (byte) 0x09, (byte) 0x66, (byte) 0x43,
        (byte) 0xB5, (byte) 0x90, (byte) 0xFF, (byte) 0xDA, (byte) 0x21, (byte) 0x04, (byte) 0x6B, (byte) 0x4E,
        (byte) 0xA2, (byte) 0x87, (byte) 0xE8, (byte) 0xCD, (byte) 0x36, (byte) 0x13, (byte) 0x7C, (byte) 0x59,
        (byte) 0xAF, (byte) 0x8A, (byte) 0xE5, (byte) 0xC0, (byte) 0x3B, (byte) 0x1E, (byte) 0x71, (byte) 0x54,
        (byte) 0x8C, (byte) 0xA9, (byte) 0xC6, (byte) 0xE3, (byte) 0x18, (byte) 0x3D, (byte) 0x52, (byte) 0x77,
        (byte) 0x81, (byte) 0xA4, (byte) 0xCB, (byte) 0xEE, (byte) 0x15, (byte) 0x30, (byte) 0x5F, (byte) 0x7A,
        (byte) 0x96, (byte) 0xB3, (byte) 0xDC, (byte) 0xF9, (byte) 0x02, (byte) 0x27, (byte) 0x48, (byte) 0x6D,
        (byte) 0x9B, (byte) 0xBE, (byte) 0xD1, (byte) 0xF4, (byte) 0x0F, (byte) 0x2A, (byte) 0x45, (byte) 0x60
    };

    public DiaconnG8Packet(HasAndroidInjector injector) {
        this.received = false;
        this.failed = false;
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

    // 패킷 인코딩 앞부분
    public ByteBuffer prefixEncode(byte msgType, int msgSeq, byte msgConEnd) {
        ByteBuffer buffer = ByteBuffer.allocate(MSG_LEN);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(SOP);
        buffer.put(msgType);
        buffer.put((byte) msgSeq);
        buffer.put(msgConEnd);
        return buffer;
    }

    // 패킷 인코딩 뒷부분
    public byte[] suffixEncode(ByteBuffer buffer) {
        int remainSize = MSG_LEN - buffer.position() - 1;
        for (int i = 0; i < remainSize; i++) {
            buffer.put(MSG_PAD);
        }
        byte crc = getCRC(buffer.array(), MSG_LEN - 1);
        buffer.put(crc);
        return buffer.array();
    }

    // 패킷 디코딩 앞부분
    protected static ByteBuffer prefixDecode(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(BT_MSG_DATA_LOC);
        return buffer;
    }

    public int getType(byte[] bytes) {
        return (bytes[MSG_TYPE_LOC] & 0xC0) >> 6;
    } //상위 2비트 획득

    public int getCmd(byte[] bytes) {
        return bytes[MSG_TYPE_LOC];
    }

    public int getSeq(byte[] bytes) {
        return bytes[MSG_SEQ_LOC];
    }

    public static int getByteToInt(ByteBuffer buffer) {
        return buffer.get() & 0xff;
    }

    public static int getShortToInt(ByteBuffer buffer) {
        return buffer.getShort() & 0xffff;
    }

    public static int getIntToInt(ByteBuffer buffer) {
        return buffer.getInt();
    }

    public static byte[] getBytes(ByteBuffer buffer, int limit) {
        ByteBuffer data = ByteBuffer.allocate(MSG_LEN);
        int orgPos = buffer.position();
        int orgLimit = buffer.limit();
        buffer.limit(buffer.position() + limit);
        data.put(buffer);
        buffer.position(orgPos);
        buffer.limit(orgLimit);
        return data.array();
    }

    // CRC 체크
    static byte getCRC(byte[] data, int length) {
        int i = 0;
        byte crc = 0;
        while (length-- != 0) {
            crc = crc_table[(crc ^ data[i]) & 0xFF];
            i++;
        }
        return crc;
    }

    // 패킷 결함 체크
    public static int defect(byte[] bytes) {
        int result = 0;
        if (bytes[0] != SOP && bytes[0] != SOP_BIG) {
            // Start Code Check
            result = 98;
        } else if ((bytes[0] == SOP && bytes.length != MSG_LEN) ||
            (bytes[0] == SOP_BIG && bytes.length != MSG_LEN_BIG)) {
            // 패킷 길이 체크
            result = 97;
        } else if (bytes[bytes.length - 1] != getCRC(bytes, bytes.length - 1)) {
            // CRC 체크
            result = 99;
        }
        return result;
    }

    public byte[] encode(int msgSeq) { return new byte[0]; }


    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (final byte b : bytes)
            sb.append(String.format("%02x ", b & 0xff));
        return sb.toString();
    }

    public static String toNarrowHex(byte[] packet) {
        StringBuilder sb = new StringBuilder();
        for (final byte b : packet)
            sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    public void handleMessage(byte[] data) { }

    public String getFriendlyName() {
        return "UNKNOWN_PACKET";
    }

    public Boolean isSuccInquireResponseResult(int result) {
        boolean isSuccess = false;
        switch (result) {
            case 16 :
                isSuccess =  true;
                break;
            case 17 :
                aapsLogger.error(LTag.PUMPCOMM, "Packet CRC error");
                break;

            case 18 :
                aapsLogger.error(LTag.PUMPCOMM, "Parameter error.");
                break;

            case 19 :
                aapsLogger.error(LTag.PUMPCOMM, "Protocol specification error.");
                break;

            default:
                aapsLogger.error(LTag.PUMPCOMM, "System error.");
                break;
        }
        return isSuccess;
    }

    public Boolean isSuccSettingResponseResult(int result) {
        boolean isSuccess = false;
        switch (result) {
            case 0:
                isSuccess = true;
                break;
            case 1:
                aapsLogger.error(LTag.PUMPCOMM, "Packet CRC error");
                break;

            case 2:
                aapsLogger.error(LTag.PUMPCOMM, "Parameter error.");
                break;

            case 3:
                aapsLogger.error(LTag.PUMPCOMM, "Protocol specification error.");
                break;

            case 4:
                aapsLogger.error(LTag.PUMPCOMM, "Eating timeout, not injectable.");
                break;

            case 6:
                aapsLogger.error(LTag.PUMPCOMM, "Pump canceled it.");
                break;

            case 7:
                aapsLogger.error(LTag.PUMPCOMM, "In the midst of other operations, limited app setup capabilities");
                break;

            case 8:
                aapsLogger.error(LTag.PUMPCOMM, "During another bolus injection, injection is restricted");
                break;

            case 9:
                aapsLogger.error(LTag.PUMPCOMM, "Basal release is required.");
                break;

            case 10:
                aapsLogger.error(LTag.PUMPCOMM, "Canceled due to the opt number did not match.");
                break;

            case 11:
                aapsLogger.error(LTag.PUMPCOMM, "Injection is not possible due to low battery.");
                break;

            case 12:
                aapsLogger.error(LTag.PUMPCOMM, "Injection is not possible due to low insulin. ");
                break;

            case 13:
                aapsLogger.error(LTag.PUMPCOMM, "Can't inject due to 1 time limit exceeded.");
                break;

            case 14:
                aapsLogger.error(LTag.PUMPCOMM, "It cannot be injected due to an excess of injection volume today");
                break;

            case 15:
                aapsLogger.error(LTag.PUMPCOMM, "After base setting is completed, base injection can be made.");
                break;

            case 32:
                aapsLogger.error(LTag.PUMPCOMM, "During LGS running, injection is restricted");
                break;

            case 33:
                aapsLogger.error(LTag.PUMPCOMM, "LGS status is ON, ON Command is declined.");
                break;

            case 34:
                aapsLogger.error(LTag.PUMPCOMM, "LGS status is OFF, OFF Command is declined.");
                break;

            case 35:
                aapsLogger.error(LTag.PUMPCOMM, "Tempbasal start is rejected  when tempbasal is running");
                break;

            case 36:
                aapsLogger.error(LTag.PUMPCOMM, "Tempbasal stop is rejected  when tempbasal is not running");
                break;

            default:
                aapsLogger.error(LTag.PUMPCOMM, "It cannot be set to a system error.");
                break;

        }
        return isSuccess;
    }
}