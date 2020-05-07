package info.nightscout.androidaps.plugins.general.autotune.AutotunePrep;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.plugins.general.autotune.data.IobInputs;
import info.nightscout.androidaps.plugins.treatments.Treatment;

public class iob {
    public IobTotal getIOB(IobInputs iobInputs) {
        IobTotal iob = new IobTotal(iobInputs.clock);


        return iob;
    }

    public List<Treatment> find_insulin (IobInputs iobInputs) {
        List<Treatment> treatments = new ArrayList<Treatment>();

        return treatments;
    }
}
