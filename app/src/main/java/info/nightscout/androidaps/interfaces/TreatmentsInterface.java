package info.nightscout.androidaps.interfaces;

import java.util.List;

import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.TempExBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.data.IobTotal;

/**
 * Created by mike on 14.06.2016.
 */
public interface TreatmentsInterface {

    void updateTotalIOBTreatments();
    void updateTotalIOBTempBasals();

    IobTotal getLastCalculationTreatments();
    IobTotal getCalculationToTimeTreatments(long time);
    IobTotal getLastCalculationTempBasals();
    IobTotal getCalculationToTimeTempBasals(long time);

    MealData getMealData();

    List<Treatment> getTreatments();
    List<Treatment> getTreatments5MinBack(long time);

    // real basals on pump
    boolean isRealTempBasalInProgress();
    TempExBasal getRealTempBasal (long time);

    void tempBasalStart(TempExBasal tempBasal);
    void tempBasalStop(long time);

    // basal that can be faked by extended boluses
    boolean isTempBasalInProgress();
    TempExBasal getTempBasal (long time);
    double getTempBasalAbsoluteRate();
    double getTempBasalRemainingMinutes();

    boolean isExtendedBoluslInProgress();
    TempExBasal getExtendedBolus (long time);
    void extendedBolusStart(TempExBasal extendedBolus);
    void extendedBolusStop(long time);

    long oldestDataAvaialable();

}
