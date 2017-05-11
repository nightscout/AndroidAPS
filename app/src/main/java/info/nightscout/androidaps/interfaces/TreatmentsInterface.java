package info.nightscout.androidaps.interfaces;

import java.util.List;

import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.data.IobTotal;

/**
 * Created by mike on 14.06.2016.
 */
public interface TreatmentsInterface {

    void updateTotalIOBTreatments();
    IobTotal getLastCalculationTreatments();
    IobTotal getCalculationToTimeTreatments(long time);
    MealData getMealData();
    List<Treatment> getTreatments();
    List<Treatment> getTreatments5MinBack(long time);
}
