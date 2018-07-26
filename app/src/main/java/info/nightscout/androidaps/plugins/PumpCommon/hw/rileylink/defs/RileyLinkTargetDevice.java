package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs;

/**
 * Created by andy on 5/19/18.
 */

public enum RileyLinkTargetDevice {
    MedtronicPump(true), //
    Omnipod(false), //
    ;

    private boolean tuneUpEnabled;

    RileyLinkTargetDevice(boolean tuneUpEnabled) {

        this.tuneUpEnabled = tuneUpEnabled;
    }

    public boolean isTuneUpEnabled() {
        return tuneUpEnabled;
    }
}
