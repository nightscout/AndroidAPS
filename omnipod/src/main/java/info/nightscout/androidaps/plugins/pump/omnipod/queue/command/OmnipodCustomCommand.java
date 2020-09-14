package info.nightscout.androidaps.plugins.pump.omnipod.queue.command;

import androidx.annotation.NonNull;

import info.nightscout.androidaps.plugins.general.command.defs.CustomCommand;

public abstract class OmnipodCustomCommand implements CustomCommand {
    private final OmnipodCustomCommandType type;

    OmnipodCustomCommand(@NonNull OmnipodCustomCommandType type) {
        this.type = type;
    }

    public final OmnipodCustomCommandType getType() {
        return type;
    }

    @Override public final String getStatusDescription() {
        return type.getDescription();
    }
}
