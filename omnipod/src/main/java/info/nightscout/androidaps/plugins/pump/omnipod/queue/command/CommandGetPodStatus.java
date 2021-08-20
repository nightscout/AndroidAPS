package info.nightscout.androidaps.plugins.pump.omnipod.queue.command;

public final class CommandGetPodStatus extends OmnipodCustomCommand {
    public CommandGetPodStatus() {
        super(OmnipodCustomCommandType.GET_POD_STATUS);
    }
}
