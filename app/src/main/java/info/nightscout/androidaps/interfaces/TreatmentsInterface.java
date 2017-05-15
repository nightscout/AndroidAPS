package info.nightscout.androidaps.interfaces;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.TempBasal;
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

    boolean isTempBasalInProgress();
    TempBasal getTempBasal (long time);
    double getTempBasalAbsoluteRate();
    double getTempBasalRemainingMinutes();
    void tempBasalStart(TempBasal tempBasal);
    void tempBasalStop(long time);

    boolean isExtendedBoluslInProgress();
    TempBasal getExtendedBolus (long time);
    void extendedBolusStart(TempBasal extendedBolus);
    void extendedBolusStop(long time);

    long oldestDataAvaialable();

}
