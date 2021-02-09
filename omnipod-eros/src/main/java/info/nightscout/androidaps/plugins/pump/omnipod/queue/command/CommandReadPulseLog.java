package info.nightscout.androidaps.plugins.pump.omnipod.queue.command;

public final class CommandReadPulseLog extends OmnipodCustomCommand {
    public CommandReadPulseLog() {
        super(OmnipodCustomCommandType.READ_PULSE_LOG);
    }
}
