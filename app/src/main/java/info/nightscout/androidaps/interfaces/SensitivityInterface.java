package info.nightscout.androidaps.interfaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;

/**
 * Created by mike on 24.06.2017.
 */

public interface SensitivityInterface {

    double MIN_HOURS = 1;
    double MIN_HOURS_FULL_AUTOSENS = 4;

    AutosensResult detectSensitivity(long fromTime, long toTime);

}
