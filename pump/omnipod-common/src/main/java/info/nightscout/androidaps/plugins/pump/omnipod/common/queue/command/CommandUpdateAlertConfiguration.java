package info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command;

import org.jetbrains.annotations.NotNull;

import app.aaps.interfaces.queue.CustomCommand;

public final class CommandUpdateAlertConfiguration implements CustomCommand {
    @NotNull @Override public String getStatusDescription() {
        return "UPDATE ALERT CONFIGURATION";
    }
}
