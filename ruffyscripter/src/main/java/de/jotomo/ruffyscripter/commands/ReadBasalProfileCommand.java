package de.jotomo.ruffyscripter.commands;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffyscripter.RuffyScripter;
import de.jotomo.ruffy.spi.CommandResult;

public class ReadBasalProfileCommand extends BaseCommand {
    private final int number;

    public ReadBasalProfileCommand(int number) {
        this.number = number;
    }

    @Override
    public CommandResult execute() {
        return new CommandResult().success(false).enacted(false);
    }

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }

    @Override
    public void setScripter(RuffyScripter scripter) {

    }
}
