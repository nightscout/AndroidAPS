package info.nightscout.androidaps.plugins.PumpCombo.scripter.commands;

import java.util.List;

import info.nightscout.androidaps.plugins.PumpCombo.scripter.BasalProfile;
import info.nightscout.androidaps.plugins.PumpCombo.scripter.RuffyScripter;

public class SetBasalProfile implements Command {
    public SetBasalProfile(BasalProfile basalProfile) {

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
