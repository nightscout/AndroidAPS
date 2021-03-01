package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command;

public interface CommandBuilder<R extends Command> {
    R build();
}
