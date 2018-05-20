package info.nightscout.androidaps.plugins.PumpMedtronic.util;

import org.joda.time.LocalTime;

import java.util.List;

/**
 * Created by andy on 5/9/18.
 */

public class MedtronicUtil {


    public static LocalTime getTimeFrom30MinInterval(int interval) {
        if (interval % 2 == 0) {
            return new LocalTime(interval / 2, 0);
        } else {
            return new LocalTime((interval - 1) / 2, 30);
        }
    }


    public static int makeUnsignedShort(int b2, int b1) {
        int k = (b2 & 0xff) << 8 | b1 & 0xff;
        return k;
    }


    public static byte[] getByteArrayFromUnsignedShort(int shortValue, boolean returnFixedSize) {
        byte highByte = (byte) (shortValue >> 8 & 0xFF);
        byte lowByte = (byte) (shortValue & 0xFF);

        if (highByte > 0) {
            return createByteArray(lowByte, highByte);
        } else {
            return returnFixedSize ? createByteArray(lowByte, highByte) : createByteArray(lowByte);
        }

    }

    public static byte[] createByteArray(byte... data) {
        return data;
    }

    public static byte[] createByteArray(List<Byte> data) {

        byte[] array = new byte[data.size()];

        for (int i = 0; i < data.size(); i++) {
            array[i] = data.get(i);
        }


        return array;
    }


    public static double decodeBasalInsulin(int i, int j) {
        return decodeBasalInsulin(makeUnsignedShort(i, j));
    }


    public static double decodeBasalInsulin(int i) {
        return (double) i / 40.0d;
    }


    public static byte[] getBasalStrokes(double amount) {
        return getBasalStrokes(amount, false);
    }

    public static byte[] getBasalStrokes(double amount, boolean returnFixedSize) {
        return getStrokes(amount, 40, returnFixedSize);
    }

    public static byte[] getBolusStrokes(double amount) {
        return getStrokes(amount, 10, false);
    }


    public static byte[] getStrokes(double amount, int strokesPerUnit, boolean returnFixedSize) {

        int length = 1;
        int scrollRate = 1;

        if (strokesPerUnit >= 40) {
            length = 2;

            // 40-stroke pumps scroll faster for higher unit values
            if (amount > 10)
                scrollRate = 4;
            else if (amount > 1)
                scrollRate = 2;
        }

        int strokes = (int) (amount * (strokesPerUnit / (scrollRate * 1.0d)));

        strokes *= scrollRate;

        return getByteArrayFromUnsignedShort(strokes, false);

    }


}
