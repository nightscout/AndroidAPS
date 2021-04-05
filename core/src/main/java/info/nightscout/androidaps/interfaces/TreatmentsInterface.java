package info.nightscout.androidaps.interfaces;

import androidx.annotation.NonNull;

import java.util.List;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.NonOverlappingIntervals;
import info.nightscout.androidaps.data.ProfileIntervals;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentUpdateReturn;

/**
 * Created by mike on 14.06.2016.
 */
public interface TreatmentsInterface {

    TreatmentServiceInterface getService();

    @Deprecated
    List<Treatment> getTreatmentsFromHistoryAfterTimestamp(long timestamp);

    boolean addToHistoryTempBasal(TemporaryBasal tempBasal);

    boolean addToHistoryExtendedBolus(ExtendedBolus extendedBolus);

    boolean addToHistoryTreatment(DetailedBolusInfo detailedBolusInfo, boolean allowUpdate);

    ProfileSwitch getProfileSwitchFromHistory(long time);

    ProfileIntervals<ProfileSwitch> getProfileSwitchesFromHistory();

    void addToHistoryProfileSwitch(ProfileSwitch profileSwitch);

    void doProfileSwitch(@NonNull final ProfileStore profileStore, @NonNull final String profileName, final int duration, final int percentage, final int timeShift, final long date);

    void doProfileSwitch(final int duration, final int percentage, final int timeShift);

    TreatmentUpdateReturn createOrUpdateMedtronic(Treatment treatment, boolean fromNightScout);

}