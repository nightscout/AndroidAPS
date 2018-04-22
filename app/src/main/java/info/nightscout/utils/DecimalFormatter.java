package info.nightscout.utils;

import java.text.DecimalFormat;

import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;

/**
 * Created by mike on 11.07.2016.
 */
public class DecimalFormatter {
    private static DecimalFormat format0dec = new DecimalFormat("0");
    private static DecimalFormat format1dec = new DecimalFormat("0.0");
    private static DecimalFormat format2dec = new DecimalFormat("0.00");
    private static DecimalFormat format3dec = new DecimalFormat("0.000");

    public static String to0Decimal(double value) {
        return format0dec.format(value);
    }

    public static String to0Decimal(double value, String unit) {
        return format0dec.format(value) + unit;
    }

    public static String to1Decimal(double value) {
        return format1dec.format(value);
    }

    public static String to1Decimal(double value, String unit) {
        return format1dec.format(value) + unit;
    }

    public static String to2Decimal(double value) {
        return format2dec.format(value);
    }

    public static String to2Decimal(double value, String unit) {
        return format2dec.format(value) + unit;
    }

    public static String to3Decimal(double value) {
        return format3dec.format(value);
    }

    public static String to3Decimal(double value, String unit) {
        return format3dec.format(value) + unit;
    }

    public static String toPumpSupportedBolus(double value) {
        return ConfigBuilderPlugin.getActivePump().getPumpDescription().bolusStep <= 0.05
                ? to2Decimal(value)
                : to1Decimal(value);
    }

    public static DecimalFormat pumpSupportedBolusFormat() {
        return ConfigBuilderPlugin.getActivePump().getPumpDescription().bolusStep <= 0.05
                ? new DecimalFormat("0.00")
                : new DecimalFormat("0.0");
    }
}
