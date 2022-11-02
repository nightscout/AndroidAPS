package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs;

/**
 * Created by andy on 4/5/19.
 */

public enum CommandValueDefinitionRLType implements CommandValueDefinitionType {
    Name, //
    Firmware, //
    SignalStrength, //
    ConnectionState, //
    Frequency, //
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
