package info.nightscout.androidaps.plugins.PumpCombo.ruffy.internal.scripter.commands;

import java.util.List;

import info.nightscout.androidaps.plugins.PumpCombo.ruffy.internal.scripter.RuffyScripter;
import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.CommandResult;

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
