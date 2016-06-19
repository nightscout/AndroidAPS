package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;

/**
 * Created by mike on 14.06.2016.
 */
public interface TempBasalsInterface {
    void updateTotalIOB();
    IobTotal getLastCalculation();
}
