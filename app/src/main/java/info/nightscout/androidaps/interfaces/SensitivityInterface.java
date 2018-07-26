package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;

/**
 * Created by mike on 24.06.2017.
 */

public interface SensitivityInterface {

    double MIN_HOURS = 1;
    double MIN_HOURS_FULL_AUTOSENS = 4;

    AutosensResult detectSensitivity(long fromTime, long toTime);

}
