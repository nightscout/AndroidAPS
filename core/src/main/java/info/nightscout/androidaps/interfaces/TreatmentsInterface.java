package info.nightscout.androidaps.interfaces;

import java.util.List;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.ExtendedBolus;
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

    TreatmentUpdateReturn createOrUpdateMedtronic(Treatment treatment, boolean fromNightScout);

}