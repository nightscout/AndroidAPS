package info.nightscout.androidaps.plugins.pump.omnipod.eros.queue.command;

import org.jetbrains.annotations.NotNull;

import info.nightscout.androidaps.queue.commands.CustomCommand;

public final class CommandGetPodStatus implements CustomCommand {
    @NotNull @Override public String getStatusDescription() {
        return "GET POD STATUS";
    }
}
