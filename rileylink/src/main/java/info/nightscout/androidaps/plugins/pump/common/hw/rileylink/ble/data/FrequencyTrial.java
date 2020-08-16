package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by geoff on 5/30/16.
 * changed by Andy 10/20/18
 */
public class FrequencyTrial {

    public int tries = 0;
    public int successes = 0;
    public Double averageRSSI = 0.0;
    public double frequencyMHz = 0.0;
    public List<Integer> rssiList = new ArrayList<>();
    public double averageRSSI2;


    public void calculateAverage() {
        int sum = 0;
        int count = 0;
        for (Integer rssi : rssiList) {
            sum += Math.abs(rssi);
            count++;
        }

        double avg = (sum / (count * 1.0d));

        if (count != 0)
            this.averageRSSI = avg * (-1);
        else
            this.averageRSSI = -99.0d;
    }
}
