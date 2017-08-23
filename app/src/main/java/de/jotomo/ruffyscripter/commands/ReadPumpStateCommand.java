package de.jotomo.ruffyscripter.commands;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffyscripter.PumpState;

public class ReadPumpStateCommand extends BaseCommand {
    @Override
    public CommandResult execute(PumpState initialPumpState) {
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
