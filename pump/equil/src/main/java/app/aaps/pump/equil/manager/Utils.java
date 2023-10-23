package app.aaps.pump.equil.manager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.List;

public class Utils {
    public static byte[] generateRandomPassword(int length) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] password = new byte[length];
        secureRandom.nextBytes(password);
        return password;
    }

    public static int bytesToInt(byte highByte, byte lowByte) {
        // 将byte转换为int，同时进行位运算合并
        int highValue = (highByte & 0xFF) << 8;
        int lowValue = lowByte & 0xFF;
        int value = highValue | lowValue;
        if (value >= 0x8000) {
            return value - 0x8000;
        }
        return value;
    }

    public final static float internalDecodeSpeedToUH(int i) {
        return new BigDecimal(i).multiply(new BigDecimal("0.00625")).floatValue();
    }

    public final static BigDecimal internalDecodeSpeedToUH2(int i) {
        return new BigDecimal(i).multiply(new BigDecimal("0.00625"));
    }

    public final static float decodeSpeedToUH(int i) {
        return new BigDecimal(i).multiply(new BigDecimal("0.00625")).floatValue();
    }

    public final static double decodeSpeedToUS(int i) {
        return internalDecodeSpeedToUH2(i).divide(new BigDecimal("3600"), 10, RoundingMode.DOWN).doubleValue();
    }

    public final static int decodeSpeedToUH(double i) {
        BigDecimal a = new BigDecimal(String.valueOf(i));
        BigDecimal b = new BigDecimal("0.00625");
        BigDecimal c = a.divide(b);
//        c.setScale(2);
        return c.intValue();
    }

    public final static double decodeSpeedToUHT(double i) {
        BigDecimal a = new BigDecimal(String.valueOf(i));
        BigDecimal b = new BigDecimal("0.00625");
        return a.divide(b).doubleValue();
    }

    public static byte[] basalToByteArray(double v) {
        int value = decodeSpeedToUH(v);
        byte[] result = new byte[2];
        result[0] = (byte) ((value >> 8) & 0xFF); // 高位
        result[1] = (byte) (value & 0xFF); // 低位
        return result;
    }

    public static byte[] basalToByteArray2(double v) {
        int value = decodeSpeedToUH(v);
        byte[] result = new byte[2];
        result[1] = (byte) ((value >> 8) & 0xFF); // 高位
        result[0] = (byte) (value & 0xFF); // 低位
        return result;
    }


    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }


    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    public static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] result = new byte[length];
        int pos = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, pos, array.length);
            pos += array.length;
        }
        return result;
    }

    public static byte[] intToBytes(int value) {
        byte[] src = new byte[4];
        src[3] = (byte) ((value >> 24) & 0xFF);
        src[2] = (byte) ((value >> 16) & 0xFF);
        src[1] = (byte) ((value >> 8) & 0xFF);
        src[0] = (byte) (value & 0xFF);
        return src;
    }

//    public static int bytesToInt(byte highByte, byte lowByte) {
//        // 将byte转换为int，同时进行位运算合并
//        int highValue = (highByte & 0xFF) << 8;
//        int lowValue = lowByte & 0xFF;
//        return highValue | lowValue;
//    }

    public static byte[] intToTwoBytes(int value) {
        byte[] bytes = new byte[2];
        bytes[1] = (byte) ((value >> 8) & 0xFF); // 取高8位并转为byte
        bytes[0] = (byte) (value & 0xFF); // 取低8位并转为byte
        return bytes;

//        int i1 = ((data[15] & 0x0f) << 8) | data[14] & 0xff;
    }

    public static byte[] convertByteArray(List<Byte> byteList) {
        byte[] byteArray = new byte[byteList.size()];
        for (int i = 0; i < byteList.size(); i++) {
            byteArray[i] = byteList.get(i);
        }
        return byteArray;
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(List<Byte> bytes) {
        if (bytes == null) return "<empty>";
        char[] hexChars = new char[bytes.size() * 2];
        for (int j = 0; j < bytes.size(); j++) {
            int v = bytes.get(j) & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "<empty>";
        final char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            final int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


}
