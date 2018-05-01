package com.gxwtech.roundtrip2.util;

/**
 * Created by geoff on 4/27/15.
 */
public class CRC {

    static final int[] crc8lookup = new int[] { 0, 155, 173, 54, 193, 90, 108, 247, 25, 130, 180, 47,
            216, 67, 117, 238, 50, 169, 159, 4, 243, 104, 94, 197, 43, 176,
            134, 29, 234, 113, 71, 220, 100, 255, 201, 82, 165, 62, 8, 147,
            125, 230, 208, 75, 188, 39, 17, 138, 86, 205, 251, 96, 151, 12,
            58, 161, 79, 212, 226, 121, 142, 21, 35, 184, 200, 83, 101, 254,
            9, 146, 164, 63, 209, 74, 124, 231, 16, 139, 189, 38, 250, 97,
            87, 204, 59, 160, 150, 13, 227, 120, 78, 213, 34, 185, 143, 20,
            172, 55, 1, 154, 109, 246, 192, 91, 181, 46, 24, 131, 116, 239,
            217, 66, 158, 5, 51, 168, 95, 196, 242, 105, 135, 28, 42, 177,
            70, 221, 235, 112, 11, 144, 166, 61, 202, 81, 103, 252, 18, 137,
            191, 36, 211, 72, 126, 229, 57, 162, 148, 15, 248, 99, 85, 206,
            32, 187, 141, 22, 225, 122, 76, 215, 111, 244, 194, 89, 174, 53,
            3, 152, 118, 237, 219, 64, 183, 44, 26, 129, 93, 198, 240, 107,
            156, 7, 49, 170, 68, 223, 233, 114, 133, 30, 40, 179, 195, 88,
            110, 245, 2, 153, 175, 52, 218, 65, 119, 236, 27, 128, 182, 45,
            241, 106, 92, 199, 48, 171, 157, 6, 232, 115, 69, 222, 41, 178,
            132, 31, 167, 60, 10, 145, 102, 253, 203, 80, 190, 37, 19, 136,
            127, 228, 210, 73, 149, 14, 56, 163, 84, 207, 249, 98, 140, 23,
            33, 186, 77, 214, 224, 123 };


    public static byte crc8(byte[] data, int len) {
        byte result = 0;
        if (data == null) {
            return 0;
        }
        if (len > data.length) {
            len = data.length;
        }
        for (int i=0; i<len; i++) {
            int tmp = result;
            int tmp2 = tmp ^ data[i];
            int tmp3 = tmp2 & 0xFF;
            int idx = tmp3;
            result = (byte)crc8lookup[idx];
//            log(String.format("iter=%d,tmp=0x%02x, tmp2=0x%02x, tmp3=0x%02x, lookup=0x%02x",i,tmp,tmp2,tmp3,result));
        }
        // orig python:
        //result = klass.lookup[ ( result ^ block[ i ] & 0xFF ) ]
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
                for (int j=0; j<data.length; j++) {
                    byte b = data[j];
                    for (int i = 0; i < 8; i++) {
                        boolean bit = ((b >> (7 - i) & 1) == 1);
                        boolean c15 = ((crc >> 15 & 1) == 1);
                        crc <<= 1;
                        if (c15 ^ bit) crc ^= polynomial;
                    }
                }
            }
        }
        crc &= 0xffff;
        return new byte[]{(byte) ((crc & 0xFF00) >> 8), (byte) (crc & 0xFF)};
    }
}
