package info.nightscout.androidaps.plugins.TuneProfile;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.treatments.Treatment;

public class Opts {
    public static List<Treatment> treatments;
    public static Profile profile;
    public List<BgReading> glucose;
    public List<Treatment> pumpHistory;
    public long start;
    public long end;
    public boolean categorize_uam_as_basal;
    public boolean tune_insulin_curve;
}
