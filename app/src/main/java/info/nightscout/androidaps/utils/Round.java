package info.nightscout.androidaps.utils;

/**
 * Created by mike on 20.06.2016.
 */
public class Round {
    public static Double roundTo(double x, Double step) {
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

    public static boolean isSame(Double d1, Double d2) {
        double diff = d1 - d2;

        return (Math.abs(diff) <= 0.000001);
    }

}
