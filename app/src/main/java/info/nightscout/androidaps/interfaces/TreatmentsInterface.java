package info.nightscout.androidaps.interfaces;

import java.util.List;

import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.utils.OverlappingIntervals;

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
    TemporaryBasal getRealTempBasal (long time);

    void tempBasalStart(TemporaryBasal tempBasal);
    void tempBasalStop(long time);

    // basal that can be faked by extended boluses
    boolean isTempBasalInProgress();
    TemporaryBasal getTempBasal (long time);
    double getTempBasalAbsoluteRate();
    double getTempBasalRemainingMinutes();
    OverlappingIntervals<TemporaryBasal> getTemporaryBasals();

    boolean isExtendedBoluslInProgress();
    ExtendedBolus getExtendedBolus (long time);
    void extendedBolusStart(ExtendedBolus extendedBolus);
    void extendedBolusStop(long time);
    OverlappingIntervals<ExtendedBolus> getExtendedBoluses();

    TempTarget getTempTarget (long time);
    OverlappingIntervals<TempTarget> getTempTargets();

    long oldestDataAvaialable();

}
