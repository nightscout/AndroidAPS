package de.jotomo.ruffyscripter.commands;

import java.util.List;

import de.jotomo.ruffyscripter.RuffyScripter;
import de.jotomo.ruffy.spi.CommandResult;

public class ReadBasalProfileCommand implements Command {
    private final int number;

    public ReadBasalProfileCommand(int number) {
        this.number = number;
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
