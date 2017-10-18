package de.jotomo.ruffyscripter.commands;

import java.util.List;

import de.jotomo.ruffy.spi.history.PumpHistory;
import de.jotomo.ruffyscripter.RuffyScripter;
import de.jotomo.ruffy.spi.CommandResult;

public class ReadHistoryCommand implements Command {
    public ReadHistoryCommand(PumpHistory knownHistory) {
    }

    @Override
    public CommandResult execute() {
        return null;
    }

    @Override
    public List<String> validateArguments() {
        return null;
    }

    @Override
    public void setScripter(RuffyScripter scripter) {

    }
}
