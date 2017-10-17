package info.nightscout.androidaps.plugins.PumpCombo.scripter.internal.commands;

import java.util.List;

import info.nightscout.androidaps.plugins.PumpCombo.spi.BasalProfile;
import info.nightscout.androidaps.plugins.PumpCombo.scripter.RuffyScripter;
import info.nightscout.androidaps.plugins.PumpCombo.spi.CommandResult;

public class SetBasalProfileCommand implements Command {
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
