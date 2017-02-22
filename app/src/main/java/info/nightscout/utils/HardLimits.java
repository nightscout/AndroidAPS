package info.nightscout.utils;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 22.02.2017.
 */

public class HardLimits {
    final static double MAXBOLUS_ADULT = 17d;
    final static double MAXBOLUS_TEENAGE = 10d;
    final static double MAXBOLUS_CHILD = 5d;

    public static double maxBolus() {
        String age = SP.getString(R.string.key_age, "");

        if (age.equals(MainApp.sResources.getString(R.string.key_adult))) return MAXBOLUS_ADULT;
        if (age.equals(MainApp.sResources.getString(R.string.key_teenage))) return MAXBOLUS_TEENAGE;
        if (age.equals(MainApp.sResources.getString(R.string.key_child))) return MAXBOLUS_CHILD;
        return MAXBOLUS_ADULT;
    }

}
