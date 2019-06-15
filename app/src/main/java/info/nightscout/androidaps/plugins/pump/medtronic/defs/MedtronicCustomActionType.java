package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;

/**
 * Created by andy on 11/3/18.
 */

public enum MedtronicCustomActionType implements CustomActionType {

    WakeUpAndTune(), //
    ClearBolusBlock(), //
    ResetRileyLinkConfiguration(), //
    ;

    @Override
    public String getKey() {
        return this.name();
    }
}
