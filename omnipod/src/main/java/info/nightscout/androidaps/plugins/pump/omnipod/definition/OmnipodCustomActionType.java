package info.nightscout.androidaps.plugins.pump.omnipod.definition;

import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;

/**
 * Created by andy on 4.8.2019
 */

public enum OmnipodCustomActionType implements CustomActionType {
    RESET_RILEY_LINK_CONFIGURATION;

    @Override
    public String getKey() {
        return this.name();
    }

}
