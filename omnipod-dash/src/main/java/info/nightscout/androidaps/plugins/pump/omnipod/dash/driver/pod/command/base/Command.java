package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable;

public interface Command extends Encodable {
    CommandType getCommandType();
}
