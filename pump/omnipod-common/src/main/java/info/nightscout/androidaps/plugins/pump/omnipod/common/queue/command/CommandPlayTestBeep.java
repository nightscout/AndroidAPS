package info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command;

import org.jetbrains.annotations.NotNull;

import info.nightscout.androidaps.queue.commands.CustomCommand;

public class CommandPlayTestBeep implements CustomCommand {
    @NotNull @Override public String getStatusDescription() {
        return "PLAY TEST BEEP";
    }
}
