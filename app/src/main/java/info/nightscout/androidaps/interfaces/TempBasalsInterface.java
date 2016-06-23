package info.nightscout.androidaps.interfaces;

import java.util.Date;

import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;

/**
 * Created by mike on 14.06.2016.
 */
public interface TempBasalsInterface {
    void updateTotalIOB();
    IobTotal getLastCalculation();

    TempBasal getTempBasal (Date time);
}
