package com.gxwtech.roundtrip2.RoundtripService.RileyLink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by geoff on 5/30/16.
 */
public class FrequencyScanResults {
    public ArrayList<FrequencyTrial> trials = new ArrayList<>();
    public double bestFrequencyMHz = 0.0;
    public void sort() {
        Collections.sort(trials, new Comparator<FrequencyTrial>() {
            @Override
            public int compare(FrequencyTrial trial1, FrequencyTrial trial2) {
                return trial1.averageRSSI.compareTo(trial2.averageRSSI);
            }
        });
    }
}
