package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.CommandValueDefinitionType;

/**
 * Created by andy on 4/5/19.
 */

public enum CommandValueDefinitionMDTType implements CommandValueDefinitionType {
    GetModel, //
    TuneUp, //
    GetProfile, //
    GetTBR, //
    ;

    @Override
    public String getName() {
        return this.name();
    }


    @Override
    public String getDescription() {
        return null;
    }


    @Override
    public String commandAction() {
        return null;
    }

}
