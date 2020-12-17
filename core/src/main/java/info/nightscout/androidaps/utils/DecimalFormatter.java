package info.nightscout.androidaps.utils;

import java.text.DecimalFormat;

import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by mike on 11.07.2016.
 */
public class DecimalFormatter {
    private static final DecimalFormat format0dec = new DecimalFormat("0");
    private static final DecimalFormat format1dec = new DecimalFormat("0.0");
    private static final DecimalFormat format2dec = new DecimalFormat("0.00");
    private static final DecimalFormat format3dec = new DecimalFormat("0.000");

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

    public static String toPumpSupportedBolus(double value, PumpInterface pump) {
        return pump.getPumpDescription().bolusStep <= 0.051
                ? to2Decimal(value)
                : to1Decimal(value);
    }

    public static String toPumpSupportedBolus(double value, PumpInterface pump, ResourceHelper resourceHelper) {
        return pump.getPumpDescription().bolusStep <= 0.051
                ? resourceHelper.gs(R.string.formatinsulinunits, value)
                : resourceHelper.gs(R.string.formatinsulinunits1, value);
    }

    public static DecimalFormat pumpSupportedBolusFormat(PumpInterface pump) {
        return pump.getPumpDescription().bolusStep <= 0.051
                ? new DecimalFormat("0.00")
                : new DecimalFormat("0.0");
    }
}
