package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs;

/**
 * Created by andy on 6/7/18.
 */

public enum RileyLinkTargetFrequency {

    Medtronic_WorldWide(868.25, 868.65, 0.05), //
    Medtronic_US(916.45, 916.80, 0.05), //
    Omnipod(416.00, 417.00, 0.05), //
    ;

    double minFrequency;
    double maxFrequency;
    double step;


    RileyLinkTargetFrequency(double minFrequency, double maxFrequency, double step) {
        this.minFrequency = minFrequency;
        this.maxFrequency = maxFrequency;
        this.step = step;
    }


    public double[] getScanFrequencies() {
        double diff = maxFrequency - minFrequency;
        int count = (int) (diff / step);

        double freq[] = new double[count];

        for(int i = 0; i < count; i++) {
            freq[i] = (minFrequency + (i * step));
        }

        return freq;
    }
}
