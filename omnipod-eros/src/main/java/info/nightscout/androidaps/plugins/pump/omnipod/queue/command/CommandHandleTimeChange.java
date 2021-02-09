package info.nightscout.androidaps.plugins.pump.omnipod.queue.command;

public final class CommandHandleTimeChange extends OmnipodCustomCommand {
    private final boolean requestedByUser;

    public CommandHandleTimeChange(boolean requestedByUser) {
        super(OmnipodCustomCommandType.HANDLE_TIME_CHANGE);
        this.requestedByUser = requestedByUser;
    }

    public boolean isRequestedByUser() {
        return requestedByUser;
    }
}
