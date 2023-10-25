package info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command;

import org.jetbrains.annotations.NotNull;

import app.aaps.core.interfaces.queue.CustomCommand;

public class CommandPlayTestBeep implements CustomCommand {
    @NotNull @Override public String getStatusDescription() {
        return "PLAY TEST BEEP";
    }
}
