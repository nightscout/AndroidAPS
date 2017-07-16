package de.jotomo.ruffyscripter.commands;

import de.jotomo.ruffyscripter.RuffyScripter;

public class ReadPumpStateCommand implements Command {
    @Override
    public CommandResult execute(RuffyScripter ruffyScripter) {
        return new CommandResult().success(true).enacted(false).message("Returning pump state only");
    }

    @Override
    public String toString() {
        return "ReadPumpStateCommand{}";
    }
}
