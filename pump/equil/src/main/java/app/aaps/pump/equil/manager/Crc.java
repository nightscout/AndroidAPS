package app.aaps.pump.equil.manager;

public class Crc {

    public static int CRC8_MAXIM(byte[] source) {
        int offset = 0;
        int length = source.length;
        int wCRCin = 0x00;
        // Integer.reverse(0x31) >>> 24
        int wCPoly = 0x8C;
        for (int i = offset, cnt = offset + length; i < cnt; i++) {
            wCRCin ^= ((long) source[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((wCRCin & 0x01) != 0) {
                    wCRCin >>= 1;
                    wCRCin ^= wCPoly;
                } else {
                    wCRCin >>= 1;
                }
            }
        }
        return wCRCin ^= 0x00;
    }


    public static byte[] getCRC(byte[] bytes) {
        int CRC = 0x0000ffff;
        int POLYNOMIAL = 0x0000a001;
        int i, j;
        for (i = 0; i < bytes.length; i++) {
            CRC ^= ((int) bytes[i] & 0x000000ff);
            for (j = 0; j < 8; j++) {
                if ((CRC & 0x00000001) != 0) {
                    CRC >>= 1;
                    CRC ^= POLYNOMIAL;
                } else {
                    CRC >>= 1;
                }
            }
        }
        String result = Integer.toHexString(CRC).toUpperCase();
        if (result.length() != 4) {
            StringBuffer sb = new StringBuffer("0000");
            result = sb.replace(4 - result.length(), 4, result).toString();
        }
        return Utils.hexStringToBytes(result);
    }

}
