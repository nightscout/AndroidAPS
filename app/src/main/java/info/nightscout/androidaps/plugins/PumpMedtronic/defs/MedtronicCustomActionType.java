package info.nightscout.androidaps.plugins.PumpMedtronic.defs;

import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;

/**
 * Created by andy on 11/3/18.
 */

public enum MedtronicCustomActionType implements CustomActionType {

    WakeUpAndTune()

    ;

    @Override
    public String getKey() {
        return this.name();
    }
}
