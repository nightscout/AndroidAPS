package info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command;

import org.jetbrains.annotations.NotNull;

import info.nightscout.interfaces.queue.CustomCommand;

public final class CommandHandleTimeChange implements CustomCommand {
    private final boolean requestedByUser;

    public CommandHandleTimeChange(boolean requestedByUser) {
        this.requestedByUser = requestedByUser;
    }

    public boolean isRequestedByUser() {
        return requestedByUser;
    }

    @NotNull @Override public String getStatusDescription() {
        return "HANDLE TIME CHANGE";
    }
}
