package info.nightscout.androidaps.plugins.PumpCombo.ruffy.internal.scripter.commands;

import java.util.List;

import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.PumpHistory;
import info.nightscout.androidaps.plugins.PumpCombo.ruffy.RuffyScripter;
import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.CommandResult;

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
