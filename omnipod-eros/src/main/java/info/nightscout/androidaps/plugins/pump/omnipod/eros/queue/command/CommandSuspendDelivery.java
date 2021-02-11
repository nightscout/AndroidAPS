package info.nightscout.androidaps.plugins.pump.omnipod.eros.queue.command;

public final class CommandSuspendDelivery extends OmnipodCustomCommand {
    public CommandSuspendDelivery() {
        super(OmnipodCustomCommandType.SUSPEND_DELIVERY);
    }
}
