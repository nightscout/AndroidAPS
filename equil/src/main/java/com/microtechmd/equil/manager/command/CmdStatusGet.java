package com.microtechmd.equil.manager.command;


import androidx.annotation.NonNull;

import info.nightscout.androidaps.queue.commands.CustomCommand;

public class CmdStatusGet implements CustomCommand {
    @NonNull @Override public String getStatusDescription() {
        return "CmdStatusGet";
    }
}
