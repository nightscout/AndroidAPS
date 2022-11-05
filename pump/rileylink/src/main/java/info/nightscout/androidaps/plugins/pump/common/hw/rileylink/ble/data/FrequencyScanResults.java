package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data;

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
        Collections.sort(trials, (trial1, trial2) -> {
            int res = trial1.averageRSSI.compareTo(trial2.averageRSSI);

            if (res == 0) {
                return (int)(trial1.frequencyMHz - trial2.frequencyMHz);
            } else
                return res;

        });
    }

}
