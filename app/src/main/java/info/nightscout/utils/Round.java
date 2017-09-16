package info.nightscout.utils;

import java.math.BigDecimal;

/**
 * Created by mike on 20.06.2016.
 */
public class Round {
    public static Double roundTo(Double x, Double step) {

        if (step > 1) {
            return roundToWhole(x, step);
        } else {
            return roundToNrOfDecimals(x, getDecimalsFromStep(step));
        }
    }

    private static int getDecimalsFromStep(Double step) {
        String stepString = Double.toString(step);
        return stepString.substring(stepString.indexOf('.') + 1).length();
    }

    private static Double roundToNrOfDecimals(Double x, int decimals) {
        BigDecimal number = new BigDecimal(Double.toString(x));
        number = number.setScale(decimals, BigDecimal.ROUND_HALF_UP);

        return number.doubleValue();
    }

    private static Double roundToWhole(Double x, Double step) {
        if (x != 0d) {
            return Math.round(x / step) * step;
        }
        return 0d;
    }

    public static Double floorTo(Double x, Double step) {
        if (x != 0d) {
            return Math.floor(x / step) * step;
        }
        return 0d;
    }
    public static Double ceilTo(Double x, Double step) {
        if (x != 0d) {
            return Math.ceil(x / step) * step;
        }
        return 0d;
    }
}
