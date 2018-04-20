package info.nightscout.androidaps.interfaces;

import java.util.Date;

import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.Treatment;

/**
 * Created by mike on 17.04.2017.
 */

public interface InsulinInterface {
    final int FASTACTINGINSULIN = 0;
    final int FASTACTINGINSULINPROLONGED = 1;
    final int OREF_RAPID_ACTING = 2;
    final int OREF_ULTRA_RAPID_ACTING = 3;
    final int OREF_FREE_PEAK = 4;


    int getId();
    String getFriendlyName();
    String getComment();
    double getDia();
    public Iob iobCalcForTreatment(Treatment treatment, long time, double dia);
}
