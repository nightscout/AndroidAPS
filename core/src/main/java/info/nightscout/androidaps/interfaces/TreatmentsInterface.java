package info.nightscout.androidaps.interfaces;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.NonOverlappingIntervals;
import info.nightscout.androidaps.data.ProfileIntervals;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentUpdateReturn;

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

    List<Treatment> getTreatmentsFromHistory();

    List<Treatment> getCarbTreatments5MinBackFromHistory(long time);

    List<Treatment> getTreatmentsFromHistoryAfterTimestamp(long timestamp);

    long getLastBolusTime();

    // real basals (not faked by extended bolus)
    boolean isInHistoryRealTempBasalInProgress();

    TemporaryBasal getRealTempBasalFromHistory(long time);

    boolean addToHistoryTempBasal(TemporaryBasal tempBasal);

    // basal that can be faked by extended boluses
    boolean isTempBasalInProgress();

    TemporaryBasal getTempBasalFromHistory(long time);

    NonOverlappingIntervals<TemporaryBasal> getTemporaryBasalsFromHistory();

    void removeTempBasal(TemporaryBasal temporaryBasal);

    boolean isInHistoryExtendedBoluslInProgress();

    ExtendedBolus getExtendedBolusFromHistory(long time);

    Intervals<ExtendedBolus> getExtendedBolusesFromHistory();

    boolean addToHistoryExtendedBolus(ExtendedBolus extendedBolus);

    boolean addToHistoryTreatment(DetailedBolusInfo detailedBolusInfo, boolean allowUpdate);

    TempTarget getTempTargetFromHistory();

    TempTarget getTempTargetFromHistory(long time);

    Intervals<TempTarget> getTempTargetsFromHistory();

    void addToHistoryTempTarget(TempTarget tempTarget);

    ProfileSwitch getProfileSwitchFromHistory(long time);

    ProfileIntervals<ProfileSwitch> getProfileSwitchesFromHistory();

    void addToHistoryProfileSwitch(ProfileSwitch profileSwitch);

    void doProfileSwitch(@NotNull final ProfileStore profileStore, @NotNull final String profileName, final int duration, final int percentage, final int timeShift, final long date);

    void doProfileSwitch(final int duration, final int percentage, final int timeShift);

    long oldestDataAvailable();

    TreatmentUpdateReturn createOrUpdateMedtronic(Treatment treatment, boolean fromNightScout);

}