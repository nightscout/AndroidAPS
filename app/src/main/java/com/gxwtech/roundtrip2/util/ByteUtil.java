package com.gxwtech.roundtrip2.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by geoff on 4/28/15.
 */
public class ByteUtil {
    private final static char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private final static String HEX_DIGITS_STR = "0123456789ABCDEF";

    public static byte highByte(short s) {
        return (byte) (s / 256);
    }

    public static byte lowByte(short s) {
        return (byte) (s % 256);
    }

    public static int asUINT8(byte b) {
        return (b < 0) ? b + 256 : b;
    }

    /* For Reference: static void System.arraycopy(Object src, int srcPos, Object dest, int destPos, int length) */

    public static byte[] concat(byte[] a, byte[] b) {
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

    public static byte[] substring(byte[] a, int start, int len) {
        byte[] rval = new byte[len];
        System.arraycopy(a, start, rval, 0, len);
        return rval;
    }

    public static String shortHexString(byte[] ra) {
        String rval = "";
        if (ra == null) {
            return rval;
        }
        if (ra.length == 0) {
            return rval;
        }
        for (int i = 0; i < ra.length; i++) {
            rval = rval + HEX_DIGITS[(ra[i] & 0xF0) >> 4];
            rval = rval + HEX_DIGITS[(ra[i] & 0x0F)];
            if (i < ra.length - 1) {
                rval = rval + " ";
            }
        }
        return rval;
    }

    public static String showPrintable(byte[] ra) {
        String s = new String();
        for (int i=0; i<ra.length; i++) {
            char c = (char)ra[i];
            if (((c>='0') && (c<='9')) ||
                    ((c>='A') && (c<='Z')) ||
                    ((c>='a') && (c<='z'))) {
                s = s+c;
            }
            else {
                s = s+'.';
            }
        }
        return s;
    }

    public static byte[] fromHexString(String src) {
        String s = src.toUpperCase();
        byte[] rval = new byte[]{};
        if ((s.length() % 2)!=0) {
            // invalid hex string!
            return null;
        }
        for (int i=0; i<s.length(); i+=2) {
            int highNibbleOrd = HEX_DIGITS_STR.indexOf(s.charAt(i));
            if (highNibbleOrd < 0) {
                // Not a hex digit.
                return null;
            }
            int lowNibbleOrd = HEX_DIGITS_STR.indexOf(s.charAt(i+1));
            if (lowNibbleOrd < 0) {
                // Not a hex digit
                return null;
            }
            rval = concat(rval,(byte)(highNibbleOrd * 16 + lowNibbleOrd));
        }
        return rval;
    }

    public static byte[] fromByteArray(List<Byte> byteArray){
        byte[] rval = new byte[byteArray.size()];
        for (int i=0; i<byteArray.size(); i++) {
            rval[i] = byteArray.get(i);
        }
        return rval;
    }
    public static ArrayList<Byte> toByteArray(byte[] data) {
        ArrayList<Byte> rval = new ArrayList<>(data.length);
        for (int i=0; i<data.length; i++) {
            rval.add(i,new Byte(data[i]));
        }
        return rval;
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
        int acc=0;
        for (i=0; i<len1; i++) {
            acc += s1[i];
            acc -= s2[i];
            if (acc != 0) {
                return acc;
            }
        }
        return 0;
    }

}
