package info.nightscout.androidaps.interfaces;

import java.util.List;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.ProfileIntervals;

/**
 * Created by mike on 14.06.2016.
 */
public interface TreatmentsInterface {

    void updateTotalIOBTreatments();
    void updateTotalIOBTempBasals();

    IobTotal getLastCalculationTreatments();
    IobTotal getCalculationToTimeTreatments(long time);
    IobTotal getLastCalculationTempBasals();
    IobTotal getCalculationToTimeTempBasals(long time, Profile profile);

    MealData getMealData();

    List<Treatment> getTreatmentsFromHistory();
    List<Treatment> getTreatments5MinBackFromHistory(long time);
    long getLastBolusTime();

    // real basals (not faked by extended bolus)
    boolean isInHistoryRealTempBasalInProgress();
    TemporaryBasal getRealTempBasalFromHistory(long time);

    boolean addToHistoryTempBasal(TemporaryBasal tempBasal);

    // basal that can be faked by extended boluses
    boolean isTempBasalInProgress();
    TemporaryBasal getTempBasalFromHistory(long time);
    Intervals<TemporaryBasal> getTemporaryBasalsFromHistory();

    boolean isInHistoryExtendedBoluslInProgress();
    ExtendedBolus getExtendedBolusFromHistory(long time);
    Intervals<ExtendedBolus> getExtendedBolusesFromHistory();

    boolean addToHistoryExtendedBolus(ExtendedBolus extendedBolus);

    boolean addToHistoryTreatment(DetailedBolusInfo detailedBolusInfo);

    TempTarget getTempTargetFromHistory();
    TempTarget getTempTargetFromHistory(long time);
    Intervals<TempTarget> getTempTargetsFromHistory();
    void addToHistoryTempTarget(TempTarget tempTarget);

    ProfileSwitch getProfileSwitchFromHistory(long time);
    ProfileIntervals<ProfileSwitch> getProfileSwitchesFromHistory();
    void addToHistoryProfileSwitch(ProfileSwitch profileSwitch);

    long oldestDataAvailable();

}
