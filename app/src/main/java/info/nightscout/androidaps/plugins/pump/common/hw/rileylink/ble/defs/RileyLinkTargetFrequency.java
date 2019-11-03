package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs;

/**
 * Created by andy on 6/7/18.
 */

public enum RileyLinkTargetFrequency {

    Medtronic_WorldWide(868.25, 868.3, 868.35, 868.4, 868.45, 868.5, 868.55, 868.6, 868.65), //
    Medtronic_US(916.45, 916.5, 916.55, 916.6, 916.65, 916.7, 916.75, 916.8), //
    Omnipod(433.91), //
    ;

    double[] frequencies;


    RileyLinkTargetFrequency(double... frequencies) {
        this.frequencies = frequencies;
    }


    public double[] getScanFrequencies() {
        return frequencies;
    }
}
