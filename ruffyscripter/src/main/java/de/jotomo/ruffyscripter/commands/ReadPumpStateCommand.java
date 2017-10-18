package de.jotomo.ruffyscripter.commands;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffyscripter.RuffyScripter;

public class ReadPumpStateCommand implements Command {
    @Override
    public CommandResult execute() {
        return new CommandResult().success(true).enacted(false);
    }

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }

    @Override
    public void setScripter(RuffyScripter scripter) {}

    @Override
    public String toString() {
        return "ReadPumpStateCommand{}";
    }
}
