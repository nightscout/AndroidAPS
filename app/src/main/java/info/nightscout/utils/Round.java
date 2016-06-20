package info.nightscout.utils;

/**
 * Created by mike on 20.06.2016.
 */
public class Round {
    public static Double roundTo(Double x, Double step) {
        if (x != 0d) {
            return Math.round(x / step) * step;
        }
        return 0d;
    }
}
