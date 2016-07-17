package info.nightscout.androidaps.interfaces;

import java.util.List;

import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsFragment;

/**
 * Created by mike on 14.06.2016.
 */
public interface TreatmentsInterface {

    void updateTotalIOB();
    IobTotal getLastCalculation();
    TreatmentsFragment.MealData getMealData();
    List<Treatment> getTreatments();
}
