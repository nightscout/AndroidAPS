package de.jotomo.ruffyscripter.commands;

import java.util.Collections;
import java.util.List;

public class ReadPumpStateCommand extends BaseCommand {
    @Override
    public CommandResult execute() {
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
