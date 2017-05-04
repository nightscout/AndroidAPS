package info.nightscout.androidaps.plugins.IobCobCalculator;

import java.util.Date;

/**
 * Created by mike on 25.04.2017.
 */

public class AutosensData {
    long time = 0L;
    public String pastSensitivity = "";
    public double deviation = 0d;
    boolean calculateWithDeviation = false;
    double absorbed = 0d;
    double carbsFromBolus = 0d;
    public double cob = 0;
    public double bgi = 0d;
    public double delta = 0d;

    public String log(long time) {
        return "AutosensData: " + new Date(time).toLocaleString() + " " + pastSensitivity + " Delta=" + delta + " Bgi=" + bgi + " Deviation=" + deviation + " Absorbed=" + absorbed + " CarbsFromBolus=" + carbsFromBolus + " COB=" + cob;
    }

}
