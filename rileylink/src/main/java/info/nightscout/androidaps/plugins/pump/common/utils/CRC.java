package info.nightscout.androidaps.plugins.pump.common.utils;

/**
 * Created by geoff on 4/27/15.
 */
public class CRC {

    static final int[] crc8lookup = new int[] {
        0, 155, 173, 54, 193, 90, 108, 247, 25,
        130,
        180,
        47,
        216,
        67,
        117,
        238,
        50,
        169, //
        159, 4, 243, 104, 94, 197, 43, 176, 134, 29, 234, 113, 71, 220, 100, 255, 201,
        82,
        165,
        62,
        8,
        147,
        125,
        230,
        208,
        75, //
        188, 39, 17, 138, 86, 205, 251, 96, 151, 12, 58, 161, 79, 212, 226, 121, 142, 21,
        35,
        184,
        200,
        83,
        101,
        254,
        9,
        146, //
        164, 63, 209, 74, 124, 231, 16, 139, 189, 38, 250, 97, 87, 204, 59, 160, 150, 13, 227,
        120,
        78,
        213,
        34,
        185,
        143,
        20, //
        172, 55, 1, 154, 109, 246, 192, 91, 181, 46, 24, 131, 116, 239, 217, 66, 158, 5, 51, 168, 95,
        196,
        242,
        105,
        135,
        28,
        42, //
        177, 70, 221, 235, 112, 11, 144, 166, 61, 202, 81, 103, 252, 18, 137, 191, 36, 211, 72, 126, 229,
        57,
        162,
        148,
        15,
        248, //
        99, 85, 206, 32, 187, 141, 22, 225, 122, 76, 215, 111, 244, 194, 89, 174, 53, 3, 152, 118, 237, 219, 64,
        183,
        44,
        26,
        129, //
        93, 198, 240, 107, 156, 7, 49, 170, 68, 223, 233, 114, 133, 30, 40, 179, 195, 88, 110, 245, 2, 153, 175, 52,
        218,
        65,
        119, //
        236, 27, 128, 182, 45, 241, 106, 92, 199, 48, 171, 157, 6, 232, 115, 69, 222, 41, 178, 132, 31, 167, 60, 10,
        145, 102,
        253, //
        203, 80, 190, 37, 19, 136, 127, 228, 210, 73, 149, 14, 56, 163, 84, 207, 249, 98, 140, 23, 33, 186, 77, 214,
        224, 123 };

    static final int[] crc16lookup = new int[] {
        0x0000, 0x8005, 0x800f, 0x000a, 0x801b, 0x001e, 0x0014, 0x8011, 0x8033, 0x0036, 0x003c, 0x8039, 0x0028, 0x802d,
        0x8027, 0x0022, 0x8063, 0x0066, 0x006c, 0x8069, 0x0078, 0x807d, 0x8077, 0x0072, 0x0050, 0x8055, 0x805f, 0x005a,
        0x804b, 0x004e, 0x0044, 0x8041, 0x80c3, 0x00c6, 0x00cc, 0x80c9, 0x00d8, 0x80dd, 0x80d7, 0x00d2, 0x00f0, 0x80f5,
        0x80ff, 0x00fa, 0x80eb, 0x00ee, 0x00e4, 0x80e1, 0x00a0, 0x80a5, 0x80af, 0x00aa, 0x80bb, 0x00be, 0x00b4, 0x80b1,
        0x8093, 0x0096, 0x009c, 0x8099, 0x0088, 0x808d, 0x8087, 0x0082, 0x8183, 0x0186, 0x018c, 0x8189, 0x0198, 0x819d,
        0x8197, 0x0192, 0x01b0, 0x81b5, 0x81bf, 0x01ba, 0x81ab, 0x01ae, 0x01a4, 0x81a1, 0x01e0, 0x81e5, 0x81ef, 0x01ea,
        0x81fb, 0x01fe, 0x01f4, 0x81f1, 0x81d3, 0x01d6, 0x01dc, 0x81d9, 0x01c8, 0x81cd, 0x81c7, 0x01c2, 0x0140, 0x8145,
        0x814f, 0x014a, 0x815b, 0x015e, 0x0154, 0x8151, 0x8173, 0x0176, 0x017c, 0x8179, 0x0168, 0x816d, 0x8167, 0x0162,
        0x8123, 0x0126, 0x012c, 0x8129, 0x0138, 0x813d, 0x8137, 0x0132, 0x0110, 0x8115, 0x811f, 0x011a, 0x810b, 0x010e,
        0x0104, 0x8101, 0x8303, 0x0306, 0x030c, 0x8309, 0x0318, 0x831d, 0x8317, 0x0312, 0x0330, 0x8335, 0x833f, 0x033a,
        0x832b, 0x032e, 0x0324, 0x8321, 0x0360, 0x8365, 0x836f, 0x036a, 0x837b, 0x037e, 0x0374, 0x8371, 0x8353, 0x0356,
        0x035c, 0x8359, 0x0348, 0x834d, 0x8347, 0x0342, 0x03c0, 0x83c5, 0x83cf, 0x03ca, 0x83db, 0x03de, 0x03d4, 0x83d1,
        0x83f3, 0x03f6, 0x03fc, 0x83f9, 0x03e8, 0x83ed, 0x83e7, 0x03e2, 0x83a3, 0x03a6, 0x03ac, 0x83a9, 0x03b8, 0x83bd,
        0x83b7, 0x03b2, 0x0390, 0x8395, 0x839f, 0x039a, 0x838b, 0x038e, 0x0384, 0x8381, 0x0280, 0x8285, 0x828f, 0x028a,
        0x829b, 0x029e, 0x0294, 0x8291, 0x82b3, 0x02b6, 0x02bc, 0x82b9, 0x02a8, 0x82ad, 0x82a7, 0x02a2, 0x82e3, 0x02e6,
        0x02ec, 0x82e9, 0x02f8, 0x82fd, 0x82f7, 0x02f2, 0x02d0, 0x82d5, 0x82df, 0x02da, 0x82cb, 0x02ce, 0x02c4, 0x82c1,
        0x8243, 0x0246, 0x024c, 0x8249, 0x0258, 0x825d, 0x8257, 0x0252, 0x0270, 0x8275, 0x827f, 0x027a, 0x826b, 0x026e,
        0x0264, 0x8261, 0x0220, 0x8225, 0x822f, 0x022a, 0x823b, 0x023e, 0x0234, 0x8231, 0x8213, 0x0216, 0x021c, 0x8219,
        0x0208, 0x820d, 0x8207, 0x0202 };


    public static byte crc8(byte[] data, int len) {
        byte result = 0;
        if (data == null) {
            return 0;
        }
        if (len > data.length) {
            len = data.length;
        }
        for (int i = 0; i < len; i++) {
            int tmp = result;
            int tmp2 = tmp ^ data[i];
            int tmp3 = tmp2 & 0xFF;
            int idx = tmp3;
            result = (byte)crc8lookup[idx];
            // log(String.format("iter=%d,tmp=0x%02x, tmp2=0x%02x, tmp3=0x%02x, lookup=0x%02x",i,tmp,tmp2,tmp3,result));
        }
        // orig python:
        // result = klass.lookup[ ( result ^ block[ i ] & 0xFF ) ]
        return result;

    }


    public static byte crc8(byte[] data) {
        return crc8(data, data.length);
    }


    public static byte[] calculate16CCITT(byte[] data) {
        int crc = 0xFFFF;
        int polynomial = 0x1021;
        if (data != null) {
            if (data.length > 0) {
                for (int j = 0; j < data.length; j++) {
                    byte b = data[j];
                    for (int i = 0; i < 8; i++) {
                        boolean bit = ((b >> (7 - i) & 1) == 1);
                        boolean c15 = ((crc >> 15 & 1) == 1);
                        crc <<= 1;
                        if (c15 ^ bit)
                            crc ^= polynomial;
                    }
                }
            }
        }
        crc &= 0xffff;
        return new byte[] { (byte)((crc & 0xFF00) >> 8), (byte)(crc & 0xFF) };
    }


    public static int crc16(byte[] bytes) {
        int crc = 0x0000;
        for (byte b : bytes) {
            crc = (crc >>> 8) ^ crc16lookup[(crc ^ b) & 0xff];
        }
        return crc;
    }
}
