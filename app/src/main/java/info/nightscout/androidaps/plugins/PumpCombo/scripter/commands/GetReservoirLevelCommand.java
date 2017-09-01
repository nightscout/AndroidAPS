package info.nightscout.androidaps.plugins.PumpCombo.scripter.commands;

import java.util.List;

public class GetReservoirLevelCommand extends BaseCommand {
    @Override
    public CommandResult execute() {
        // TODO stub
        // watch out, level goes into PumpState, which is usually set by RuffyScripter
        // after a command ran, unless a command has already set it ... I don't like
        // that, it's too implicit ...

        // also, maybe ditch this command and add a parameter to GetPumpStateCommand to also
        // read the reservoir level if possible (pump must be in a state to accept commands
        // (possible on main, stop ...)
        return null;
    }

    @Override
    public List<String> validateArguments() {
        // TODO stub
        return null;
    }
}
