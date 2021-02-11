package info.nightscout.androidaps.plugins.pump.omnipod.eros.queue.command;

public final class CommandAcknowledgeAlerts extends OmnipodCustomCommand {
    public CommandAcknowledgeAlerts() {
        super(OmnipodCustomCommandType.ACKNOWLEDGE_ALERTS);
    }
}
