package info.nightscout.androidaps.interfaces;

import java.util.List;

import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;

/**
 * Created by mike on 14.06.2016.
 */
public interface TreatmentsInterface {

    void updateTotalIOB();
    IobTotal getLastCalculation();
    TreatmentsPlugin.MealData getMealData();
    List<Treatment> getTreatments();
}
