package info.nightscout.androidaps.plugins.pump.omnipod.queue.command;

public final class CommandAcknowledgeAlerts extends OmnipodCustomCommand {
    public CommandAcknowledgeAlerts() {
        super(OmnipodCustomCommandType.ACKNOWLEDGE_ALERTS);
    }
}
