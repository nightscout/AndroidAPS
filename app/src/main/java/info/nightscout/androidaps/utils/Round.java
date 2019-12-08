package info.nightscout.androidaps.utils;

import java.math.BigDecimal;

/**
 * Created by mike on 20.06.2016.
 */
public class Round {
    public static Double roundTo(double x, Double step) {
        if (x == 0d) {
            return 0d;
        }

        //Double oldCalc = Math.round(x / step) * step;
        Double newCalc = BigDecimal.valueOf(Math.round(x / step)).multiply(BigDecimal.valueOf(step)).doubleValue();

        // just for the tests, forcing failures
        //newCalc = oldCalc;

        return newCalc;
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
