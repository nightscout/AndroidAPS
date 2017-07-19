package de.jotomo.ruffyscripter.commands;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffyscripter.PumpState;
import de.jotomo.ruffyscripter.RuffyScripter;

public class ReadPumpStateCommand implements Command {
    @Override
    public CommandResult execute(RuffyScripter ruffyScripter, PumpState initialPumpState) {
        return new CommandResult().success(true).enacted(false).message("Returning pump state only");
    }

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "ReadPumpStateCommand{}";
    }
}
