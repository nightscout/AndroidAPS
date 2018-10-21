package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by geoff on 5/30/16.
 * changed by Andy 10/20/18
 */
public class FrequencyScanResults {

    public List<FrequencyTrial> trials = new ArrayList<>();
    public double bestFrequencyMHz = 0.0;
    public long dateTime;


    public void sort() {
        Collections.sort(trials, (trial1, trial2) -> trial1.averageRSSI.compareTo(trial2.averageRSSI));
    }

}
