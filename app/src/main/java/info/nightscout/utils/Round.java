package info.nightscout.utils;

import java.math.BigDecimal;

/**
 * Created by mike on 20.06.2016.
 */
public class Round {
    public static Double roundTo(Double x, Double step) {
        return round(x, step, BigDecimal.ROUND_HALF_UP);
    }

    public static Double floorTo(Double x, Double step) {
        return round(x, step, BigDecimal.ROUND_FLOOR);
    }

    public static Double ceilTo(Double x, Double step) {
        return round(x, step, BigDecimal.ROUND_CEILING);
    }

    private static Double round(Double x, Double step, int roundingMode) {
        BigDecimal numberToRound = new BigDecimal((Double.toString(x)));
        BigDecimal stepSize = new BigDecimal((Double.toString(step)));
        int scale = getDecimalsFromStep(step);

        numberToRound.setScale(scale, BigDecimal.ROUND_HALF_UP);

        BigDecimal rounded = numberToRound.divide(stepSize, 0, roundingMode).multiply(stepSize);

        return rounded.doubleValue();
    }

    private static int getDecimalsFromStep(Double step) {
        String stepString = Double.toString(step);
        return stepString.substring(stepString.indexOf('.') + 1).length();
    }
}
