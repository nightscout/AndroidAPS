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

    int getId();
    String getFriendlyName();
    String getComment();
    double getDia();
    public Iob iobCalc(Treatment treatment, long time, Double dia);
}
