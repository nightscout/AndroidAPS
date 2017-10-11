package info.nightscout.androidaps.plugins.PumpCombo.scripter.commands;

import java.util.List;

import info.nightscout.androidaps.plugins.PumpCombo.scripter.PumpHistory;
import info.nightscout.androidaps.plugins.PumpCombo.scripter.RuffyScripter;

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
