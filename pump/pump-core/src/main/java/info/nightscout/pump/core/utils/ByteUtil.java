package info.nightscout.pump.core.utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by geoff on 4/28/15.
 */
public class ByteUtil {

    private final static char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private final static String HEX_DIGITS_STR = "0123456789ABCDEF";


    /** @noinspection SpellCheckingInspection*/
    public static int asUINT8(byte b) {
        return (b < 0) ? b + 256 : b;
    }

    public static byte[] getBytesFromInt16(int value) {
        byte[] array = getBytesFromInt(value);
        return new byte[]{array[2], array[3]};
    }

    public static byte[] getBytesFromInt(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    /* For Reference: static void System.arraycopy(Object src, int srcPos, Object dest, int destPos, int length) */

    public static byte[] concat(byte[] a, byte[] b) {

        if (b == null) {
            return a;
        }

        int aLen = a.length;
        int bLen = b.length;
        byte[] c = new byte[aLen + bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }


    public static byte[] concat(byte[] a, byte b) {
        int aLen = a.length;
        byte[] c = new byte[aLen + 1];
        System.arraycopy(a, 0, c, 0, aLen);
        c[aLen] = b;
        return c;
    }


    public static byte[] concat(byte a, byte[] b) {
        int aLen = b.length;
        byte[] c = new byte[aLen + 1];
        c[0] = a;
        System.arraycopy(b, 0, c, 1, aLen);

        return c;
    }


    public static byte[] substring(byte[] a, int start, int len) {
        byte[] rVal = new byte[len];
        System.arraycopy(a, start, rVal, 0, len);
        return rVal;
    }

    public static byte[] substring(List<Byte> a, int start, int len) {
        byte[] rVal = new byte[len];

        for (int i = start, j = 0; i < start + len; i++, j++) {
            rVal[j] = a.get(i);
        }
        return rVal;
    }


    public static byte[] substring(byte[] a, int start) {
        int len = a.length - start;
        byte[] rVal = new byte[len];
        System.arraycopy(a, start, rVal, 0, len);
        return rVal;
    }


    public static String shortHexString(byte[] ra) {
        StringBuilder rVal = new StringBuilder();
        if (ra == null) {
            return rVal.toString();
        }
        if (ra.length == 0) {
            return rVal.toString();
        }
        for (int i = 0; i < ra.length; i++) {
            rVal.append(HEX_DIGITS[(ra[i] & 0xF0) >> 4]);
            rVal.append(HEX_DIGITS[(ra[i] & 0x0F)]);
            if (i < ra.length - 1) {
                rVal.append(" ");
            }
        }
        return rVal.toString();
    }

    public static String shortHexStringWithoutSpaces(byte[] byteArray) {
        StringBuilder hexString = new StringBuilder();
        if (byteArray == null) {
            return hexString.toString();
        }
        for (byte b : byteArray) {
            hexString.append(HEX_DIGITS[(b & 0xF0) >> 4]);
            hexString.append(HEX_DIGITS[(b & 0x0F)]);
        }
        return hexString.toString();
    }

    public static String shortHexString(List<Byte> list) {

        byte[] aByte0 = getByteArrayFromList(list);

        return shortHexString(aByte0);
    }


    public static String shortHexString(byte val) {
        return getHexCompact(val);
    }


    public static byte[] fromHexString(String src) {
        String s = src.toUpperCase();
        byte[] rVal = new byte[]{};
        if ((s.length() % 2) != 0) {
            // invalid hex string!
            return null;
        }
        for (int i = 0; i < s.length(); i += 2) {
            int highNibbleOrd = HEX_DIGITS_STR.indexOf(s.charAt(i));
            if (highNibbleOrd < 0) {
                // Not a hex digit.
                return null;
            }
            int lowNibbleOrd = HEX_DIGITS_STR.indexOf(s.charAt(i + 1));
            if (lowNibbleOrd < 0) {
                // Not a hex digit
                return null;
            }
            rVal = concat(rVal, (byte) (highNibbleOrd * 16 + lowNibbleOrd));
        }
        return rVal;
    }


    public static List<Byte> getListFromByteArray(byte[] array) {
        List<Byte> listOut = new ArrayList<>();

        for (byte val : array) {
            listOut.add(val);
        }

        return listOut;
    }


    public static byte[] getByteArrayFromList(List<Byte> list) {
        byte[] out = new byte[list.size()];

        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }

        return out;
    }


    // compares byte strings like strcmp
    public static int compare(byte[] s1, byte[] s2) {
        int i;
        int len1 = s1.length;
        int len2 = s2.length;
        if (len1 > len2) {
            return 1;
        }
        if (len2 > len1) {
            return -1;
        }
        int acc = 0;
        for (i = 0; i < len1; i++) {
            acc += s1[i];
            acc -= s2[i];
            if (acc != 0) {
                return acc;
            }
        }
        return 0;
    }


    /**
     * Converts 4 (or less) ints into int. (Shorts are objects, so you can send null if you have less parameters)
     *
     * @param b1   short 1
     * @param b2   short 2
     * @param b3   short 3
     * @param b4   short 4
     * @param flag Conversion Flag (Big Endian, Little endian)
     * @return int value
     */
    public static int toInt(Integer b1, Integer b2, Integer b3, Integer b4, BitConversion flag) {
        switch (flag) {
            case LITTLE_ENDIAN: {
                if (b4 != null) {
                    return (b4 & 0xff) << 24 | (b3 & 0xff) << 16 | (b2 & 0xff) << 8 | b1 & 0xff;
                } else if (b3 != null) {
                    return (b3 & 0xff) << 16 | (b2 & 0xff) << 8 | b1 & 0xff;
                } else if (b2 != null) {
                    return (b2 & 0xff) << 8 | b1 & 0xff;
                } else {
                    return b1 & 0xff;
                }
            }

            default:
            case BIG_ENDIAN: {
                if (b4 != null) {
                    return (b1 & 0xff) << 24 | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | b4 & 0xff;
                } else if (b3 != null) {
                    return (b1 & 0xff) << 16 | (b2 & 0xff) << 8 | b3 & 0xff;
                } else if (b2 != null) {
                    return (b1 & 0xff) << 8 | b2 & 0xff;
                } else {
                    return b1 & 0xff;
                }
            }
        }
    }


    /**
     * Converts 4 (or less) ints into int. (Shorts are objects, so you can send null if you have less parameters)
     *
     * @param b1   short 1
     * @param b2   short 2
     * @param b3   short 3
     * @param b4   short 4
     * @param flag Conversion Flag (Big Endian, Little endian)
     * @return int value
     */
    public static int toInt(Byte b1, Byte b2, Byte b3, Byte b4, BitConversion flag) {
        switch (flag) {
            case LITTLE_ENDIAN: {
                if (b4 != null) {
                    return (b4 & 0xff) << 24 | (b3 & 0xff) << 16 | (b2 & 0xff) << 8 | b1 & 0xff;
                } else if (b3 != null) {
                    return (b3 & 0xff) << 16 | (b2 & 0xff) << 8 | b1 & 0xff;
                } else if (b2 != null) {
                    return (b2 & 0xff) << 8 | b1 & 0xff;
                } else {
                    return b1 & 0xff;
                }
            }

            default:
            case BIG_ENDIAN: {
                if (b4 != null) {
                    return (b1 & 0xff) << 24 | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | b4 & 0xff;
                } else if (b3 != null) {
                    return (b1 & 0xff) << 16 | (b2 & 0xff) << 8 | b3 & 0xff;
                } else if (b2 != null) {
                    return (b1 & 0xff) << 8 | b2 & 0xff;
                } else {
                    return b1 & 0xff;
                }
            }
        }
    }


    public static int toInt(int b1, int b2) {
        return toInt(b1, b2, null, null, BitConversion.BIG_ENDIAN);
    }

    public static int toInt(Byte b1, Byte b2) {
        return toInt(b1, b2, null, null, BitConversion.BIG_ENDIAN);
    }


    public static int toInt(int b1, int b2, int b3) {
        return toInt(b1, b2, b3, null, BitConversion.BIG_ENDIAN);
    }

    public static int toInt(Byte b1, Byte b2, Byte b3) {
        return toInt(b1, b2, b3, null, BitConversion.BIG_ENDIAN);
    }

    public static int toInt(int b1, int b2, BitConversion flag) {
        return toInt(b1, b2, null, null, flag);
    }


    public static int makeUnsignedShort(int i, int j) {
        return (i & 0xff) << 8 | j & 0xff;
    }


    public static String getCorrectHexValue(byte inp) {
        String hx = Integer.toHexString((char) inp);

        if (hx.length() == 1)
            return "0" + hx;
        else if (hx.length() == 2)
            return hx;
        else if (hx.length() == 4)
            return hx.substring(2);
        else {
            System.out.println("Hex Error: " + inp);
        }

        return null;
    }


    public static String getHex(byte[] aByte0) {
        return aByte0 != null ? getHex(aByte0, aByte0.length) : null;
    }


    public static String getString(short[] aByte0) {
        StringBuilder sb = new StringBuilder();

        for (short i : aByte0) {
            sb.append(i);
            sb.append(" ");
        }

        return sb.toString();
    }


    public static String getHex(List<Byte> list) {

        byte[] aByte0 = getByteArrayFromList(list);

        return getHex(aByte0, aByte0.length);
    }


    public static String getHex(byte[] aByte0, int i) {
        StringBuilder sb = new StringBuilder();
        if (aByte0 != null) {
            i = Math.min(i, aByte0.length);
            for (int j = 0; j < i; j++) {
                sb.append(shortHexString(aByte0[j]));
                if (j < i - 1) {
                    sb.append(" ");
                }
            }

        }
        return new String(sb);
    }


    public static String getHex(byte byte0) {
        String s = byte0 != -1 ? "0x" : "";
        return s + getHexCompact(byte0);
    }


    public static String getHexCompact(byte byte0) {
        int i = byte0 != -1 ? convertUnsignedByteToInt(byte0) : (int) byte0;
        return getHexCompact(i);
    }


    public static int convertUnsignedByteToInt(byte data) {
        return data & 0xff;
    }


    public static String getHexCompact(int l) {
        String s = Long.toHexString(l).toUpperCase();
        String s1 = isOdd(s.length()) ? "0" : "";
        return l != -1L ? s1 + s : "-1";
    }


    public static boolean isEven(int i) {
        return i % 2 == 0;
    }


    public static boolean isOdd(int i) {
        return !isEven(i);
    }

    public enum BitConversion {
        LITTLE_ENDIAN, // 20 0 0 0 = reverse
        BIG_ENDIAN // 0 0 0 20 = normal - java
    }


    // 000300050100C800A0
    public static byte[] createByteArrayFromCompactString(String dataFull) {
        return createByteArrayFromCompactString(dataFull, 0, dataFull.length());
    }


    // 00 03 00 05 01 00 C8 00 A0
    public static byte[] createByteArrayFromString(String dataFull) {

        String data = dataFull.replace(" ", "");

        return createByteArrayFromCompactString(data, 0, data.length());
    }


    public static byte[] createByteArrayFromHexString(String dataFull) {

        String data = dataFull.replace(" 0x", "");
        data = data.replace("0x", "");

        return createByteArrayFromCompactString(data, 0, data.length());
    }


    public static byte[] createByteArrayFromCompactString(String dataFull, int startIndex, int length) {

        String data = dataFull.substring(startIndex);

        data = data.substring(0, length);

        int len = data.length();
        byte[] outArray = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            outArray[i / 2] = (byte) ((Character.digit(data.charAt(i), 16) << 4) + Character.digit(data.charAt(i + 1),
                    16));
        }

        return outArray;
    }


}
