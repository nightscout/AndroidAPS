package info.nightscout.androidaps.plugins.TuneProfile;

import java.util.ArrayList;
import java.util.List;

public class PrepOutput {
    public List<CRDatum> CRData = new ArrayList<CRDatum>();
    public List<BGDatum> CSFGlucoseData = new ArrayList<BGDatum>();
    public List<BGDatum> ISFGlucoseData = new ArrayList<BGDatum>();
    public List<BGDatum> basalGlucoseData = new ArrayList<BGDatum>();
    public String JSONString;
}
