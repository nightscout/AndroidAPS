package info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command;

import org.jetbrains.annotations.NotNull;

import app.aaps.core.interfaces.queue.CustomCommand;

public final class CommandDeactivatePod implements CustomCommand {
    @NotNull @Override public String getStatusDescription() {
        return "DEACTIVATE POD";
    }
}
