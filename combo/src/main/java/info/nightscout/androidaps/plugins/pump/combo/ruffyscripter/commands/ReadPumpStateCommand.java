package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

public class ReadPumpStateCommand extends BaseCommand {
    @Override
    public void execute() {
        // nothing to do, scripter adds state to all command results
        result.success = true;
    }

    @Override
    public String toString() {
        return "ReadPumpStateCommand{}";
    }

    @Override
    public boolean needsRunMode() {
        return false;
    }
}
