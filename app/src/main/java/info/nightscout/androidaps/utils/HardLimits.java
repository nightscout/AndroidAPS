package info.nightscout.androidaps.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;

/**
 * Created by mike on 22.02.2017.
 */

public class HardLimits {
    private static Logger log = LoggerFactory.getLogger(HardLimits.class);

    final static int CHILD = 0;
    final static int TEENAGE = 1;
    final static int ADULT = 2;
    final static int RESISTANTADULT = 3;

    final static double[] MAXBOLUS = {5d, 10d, 17d, 25d};

    // Very Hard Limits Ranges
    // First value is the Lowest and second value is the Highest a Limit can define
    public static final int[] VERY_HARD_LIMIT_MIN_BG = {72, 180};
    public static final int[] VERY_HARD_LIMIT_MAX_BG = {90, 270};
    public static final int[] VERY_HARD_LIMIT_TARGET_BG = {80, 200};
    // Very Hard Limits Ranges for Temp Targets
    public static final int[] VERY_HARD_LIMIT_TEMP_MIN_BG = {72, 180};
    public static final int[] VERY_HARD_LIMIT_TEMP_MAX_BG = {72, 270};
    public static final int[] VERY_HARD_LIMIT_TEMP_TARGET_BG = {72, 200};

    public static final double MINDIA = 2;
    public static final double MAXDIA = 7;

    public static final double MINIC = 2;
    public static final double MAXIC = 100;

    public static final double MINISF = 2; // mgdl
    public static final double MAXISF = 720; // mgdl

    public static final double[] MAXIOB_AMA = {3, 5, 7, 12};
    public static final double[] MAXIOB_SMB = {3, 7, 12, 25};

    public static final double[] MAXBASAL = {2, 5, 10, 12};


    private static int loadAge() {
        String sp_age = SP.getString(R.string.key_age, "");
        int age;

        if (sp_age.equals(MainApp.gs(R.string.key_child)))
            age = CHILD;
        else if (sp_age.equals(MainApp.gs(R.string.key_teenage)))
            age = TEENAGE;
        else if (sp_age.equals(MainApp.gs(R.string.key_adult)))
            age = ADULT;
        else if (sp_age.equals(MainApp.gs(R.string.key_resistantadult)))
            age = RESISTANTADULT;
        else age = ADULT;

        return age;
    }

    public static double maxBolus() {
        return MAXBOLUS[loadAge()];
    }

    public static double maxIobAMA() {
        return MAXIOB_AMA[loadAge()];
    }

    public static double maxIobSMB() {
        return MAXIOB_SMB[loadAge()];
    }

    public static double maxBasal() {
        return MAXBASAL[loadAge()];
    }

    // safety checks
    public static boolean checkOnlyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        return value.equals(verifyHardLimits(value, valueName, lowLimit, highLimit));
    }

    public static Double verifyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        Double newvalue = value;
        if (newvalue < lowLimit || newvalue > highLimit) {
            newvalue = Math.max(newvalue, lowLimit);
            newvalue = Math.min(newvalue, highLimit);
            String msg = String.format(MainApp.gs(R.string.valueoutofrange), valueName);
            msg += ".\n";
            msg += String.format(MainApp.gs(R.string.valuelimitedto), value, newvalue);
            log.error(msg);
            NSUpload.uploadError(msg);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), msg, R.raw.error);
        }
        return newvalue;
    }
}
