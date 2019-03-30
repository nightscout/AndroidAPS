package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.plugins.treatments.Treatment;

/**
 * Created by mike on 17.04.2017.
 */

public interface InsulinInterface {
    // int FASTACTINGINSULIN = 0; // old model no longer available
    // int FASTACTINGINSULINPROLONGED = 1; // old model no longer available
    int OREF_RAPID_ACTING = 2;
    int OREF_ULTRA_RAPID_ACTING = 3;
    int OREF_FREE_PEAK = 4;


    int getId();
    String getFriendlyName();
    String getComment();
    double getDia();
    Iob iobCalcForTreatment(Treatment treatment, long time, double dia);
}
