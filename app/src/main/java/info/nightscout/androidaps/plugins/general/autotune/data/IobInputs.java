package info.nightscout.androidaps.plugins.general.autotune.data;

import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.general.autotune.data.NsTreatment;
import java.util.List;

public class IobInputs {
    public static List<Treatment> treatments;
    public static TunedProfile profile;
    public static List<CareportalEvent> careportalEvents;
    public List<NsTreatment> history;
    public Double currentBasal;
    public long clock;


}
