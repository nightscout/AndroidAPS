package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.RuffyScripter;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.CommandResult;

/**
 * Interface for all commands to be executed by the pump.
 * <p>
 * Note on cammond methods and timing: a method shall wait before and after executing
 * as necessary to not cause timing issues, so the caller can just call methods in
 * sequence, letting the methods take care of waits.
 */
public interface Command {
    void setScripter(RuffyScripter scripter);
    List<String> validateArguments();
    boolean needsRunMode();
    void execute();
    CommandResult getResult();
    Integer getReconnectWarningId();
}
