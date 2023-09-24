package info.nightscout.androidaps.plugins.pump.omnipod.eros.queue.command;

import org.jetbrains.annotations.NotNull;

import app.aaps.interfaces.queue.CustomCommand;

public final class CommandReadPulseLog implements CustomCommand {
    @NotNull @Override public String getStatusDescription() {
        return "READ PULSE LOG";
    }
}
