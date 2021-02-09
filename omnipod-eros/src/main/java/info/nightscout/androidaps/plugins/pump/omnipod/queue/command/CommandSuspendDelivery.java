package info.nightscout.androidaps.plugins.pump.omnipod.queue.command;

public final class CommandSuspendDelivery extends OmnipodCustomCommand {
    public CommandSuspendDelivery() {
        super(OmnipodCustomCommandType.SUSPEND_DELIVERY);
    }
}
