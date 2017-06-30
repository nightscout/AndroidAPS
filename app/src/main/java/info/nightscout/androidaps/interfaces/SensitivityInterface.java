package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;

/**
 * Created by mike on 24.06.2017.
 */

public interface SensitivityInterface {
    AutosensResult detectSensitivity(long fromTime, long toTime);
}
