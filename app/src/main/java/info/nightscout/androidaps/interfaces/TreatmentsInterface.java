package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsFragment;

/**
 * Created by mike on 14.06.2016.
 */
public interface TreatmentsInterface {
    void updateTotalIOBIfNeeded();
    IobTotal getLastCalculation();
    TreatmentsFragment.MealData getMealData();
}
