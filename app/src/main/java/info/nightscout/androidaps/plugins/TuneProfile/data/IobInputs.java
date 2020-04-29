package info.nightscout.androidaps.plugins.TuneProfile.data;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.TuneProfile.data.NsTreatment;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import java.util.List;

public class IobInputs {
    public static List<Treatment> treatments;
    public static TunedProfile profile;
    public List<NsTreatment> history;
    public Double currentBasal;
    public long clock;


}
