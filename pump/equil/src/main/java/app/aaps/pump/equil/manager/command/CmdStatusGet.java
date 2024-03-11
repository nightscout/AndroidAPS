package app.aaps.pump.equil.manager.command;


import androidx.annotation.NonNull;

import app.aaps.core.interfaces.queue.CustomCommand;


public class CmdStatusGet implements CustomCommand {
    @NonNull @Override public String getStatusDescription() {
        return "CmdStatusGet";
    }
}
