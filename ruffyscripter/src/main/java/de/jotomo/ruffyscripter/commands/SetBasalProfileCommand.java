package de.jotomo.ruffyscripter.commands;

import java.util.List;

import de.jotomo.ruffy.spi.BasalProfile;
import de.jotomo.ruffyscripter.RuffyScripter;
import de.jotomo.ruffy.spi.CommandResult;

public class SetBasalProfileCommand extends BaseCommand {
    public SetBasalProfileCommand(BasalProfile basalProfile) {

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
